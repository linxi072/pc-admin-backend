package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.oauth2.OpenApiClientRules;
import io.jsonwebtoken.Jwts;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * OAuth2 授权端点（{@code GET /oauth2/authorize}，{@code response_type=code}）。
 *
 * <p><b>资源拥有者必须已认证</b>：本端点不加入 {@code SecurityConfig#BASE_PUBLIC_API}，
 * 由资源服务器过滤器链要求其携带合法 Bearer 令牌（即已登录用户）；未认证返回 401 / {@code login_required}。
 * 这与本平台「无状态 Bearer / 无 Cookie 会话」架构一致——登录态由调用方已有的访问令牌证明。</p>
 *
 * <p>流程（#900 增强）：先校验 client_id 为启用中客户端、PKCE 参数（强制 PKCE 客户端必带 {@code code_challenge}）、
 * 请求 scope 为其授权范围子集、{@code redirect_uri} 命中客户端登记白名单；再判断用户同意(consent)——
 * 已有覆盖所请求 scope 的已批准同意则跳过同意页直接签发授权码；否则返回 {@code consent_required} 与签名
 * {@code consent_token}，由前端展示同意页后回传 {@code POST /oauth2/consent} 完成授权。授权码绑定 PKCE 挑战、
 * scope 与 redirect_uri（见 {@link OAuth2AuthorizationCodeService}）。</p>
 *
 * <p>RFC 6749 中授权码应以 302 重定向回 {@code redirect_uri} 下发；本平台为 API 优先，直接以 JSON 返回，
 * 由调用方（通常是前端 SPA）将 code 交给令牌端点兑换，等效达成授权委托。</p>
 */
@RestController
@RequestMapping("/oauth2")
public class OAuth2AuthorizeEndpoint {

    /** 同意令牌有效期：仅用于短暂传递授权意图给同意页，默认 5 分钟。 */
    private static final Duration CONSENT_TTL = Duration.ofMinutes(5);

    private final OAuth2ClientDataPort clientPort;
    private final OAuth2AuthorizationCodeService codeService;
    private final OAuth2ConsentService consentService;
    private final RsaKeyProvider keys;

    public OAuth2AuthorizeEndpoint(OAuth2ClientDataPort clientPort,
                                   OAuth2AuthorizationCodeService codeService,
                                   OAuth2ConsentService consentService,
                                   RsaKeyProvider keys) {
        this.clientPort = clientPort;
        this.codeService = codeService;
        this.consentService = consentService;
        this.keys = keys;
    }

    @GetMapping("/authorize")
    public ResponseEntity<Map<String, Object>> authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod) {

        if (!"code".equals(responseType)) {
            return error(HttpStatus.BAD_REQUEST, "unsupported_response_type", "仅支持 response_type=code");
        }

        OAuth2ClientView client;
        try {
            client = clientPort.getActive(clientId);
        } catch (BusinessException e) {
            return error(HttpStatus.UNAUTHORIZED, "invalid_client", "客户端无效或未启用");
        }

        // PKCE（RFC 7636）：强制 PKCE 客户端必须携带 code_challenge；携带时结构须合法
        if (client.pkceRequired() && (codeChallenge == null || codeChallenge.isBlank())) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request", "此客户端强制要求 PKCE（code_challenge 必填）");
        }
        if (codeChallenge != null && !codeChallenge.isBlank()) {
            List<String> pkceErrors = OAuth2TokenRules.validatePkce(codeChallenge, codeChallengeMethod);
            if (!pkceErrors.isEmpty()) {
                return error(HttpStatus.BAD_REQUEST, "invalid_request", pkceErrors.get(0));
            }
        }

        Set<String> clientScopes = client.scopes();
        Set<String> reqScopes = (scope == null || scope.isBlank())
                ? Set.of() : OpenApiClientRules.parseScopes(scope);
        if (!clientScopes.containsAll(reqScopes)) {
            return error(HttpStatus.BAD_REQUEST, "invalid_scope", "请求的 scope 超出客户端授权范围");
        }

        // redirect_uri 白名单（非空时精确匹配；空=向后兼容不约束）
        if (!OpenApiClientRules.isRedirectUriAllowed(client.redirectUris(), redirectUri)) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request", "redirect_uri 不在客户端登记的白名单中");
        }

        CurrentPrincipal principal = currentPrincipal();
        if (principal == null || principal.userId() == null) {
            return error(HttpStatus.UNAUTHORIZED, "login_required", "资源拥有者未认证");
        }

        String grantedScope = reqScopes.isEmpty() ? String.join(",", clientScopes) : scope;
        Set<String> grantedScopeSet = reqScopes.isEmpty() ? clientScopes : reqScopes;

        // 用户同意(consent)：已有覆盖所请求 scope 的已批准同意则跳过同意页
        if (!consentService.isApproved(principal.userId(), clientId, grantedScopeSet)) {
            String consentToken = issueConsentToken(principal, clientId, grantedScope,
                    redirectUri, state, codeChallenge, codeChallengeMethod);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "consent_required");
            body.put("consent_token", consentToken);
            body.put("client_id", clientId);
            body.put("scopes", grantedScopeSet);
            if (state != null) {
                body.put("state", state);
            }
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        }

        OAuth2AuthorizationCodeService.CodeContext ctx = new OAuth2AuthorizationCodeService.CodeContext(
                principal.userId(), principal.username(), principal.tokenVersion(),
                principal.permissions(), principal.roles(), clientId, grantedScope, redirectUri,
                codeChallenge, codeChallengeMethod);
        String code = codeService.issue(clientId, redirectUri, ctx);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        if (state != null) {
            body.put("state", state);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private String issueConsentToken(CurrentPrincipal principal, String clientId, String scope,
                                     String redirectUri, String state, String codeChallenge,
                                     String codeChallengeMethod) {
        RSAPrivateKey key = keys.getPrivateKey();
        Date now = new Date();
        Date exp = new Date(now.getTime() + CONSENT_TTL.toMillis());
        return Jwts.builder()
                .setHeaderParam("kid", keys.getKeyId())
                .subject(principal.userId().toString())
                .claim("client_id", clientId)
                .claim("scope", scope == null ? "" : scope)
                .claim("redirect_uri", redirectUri == null ? "" : redirectUri)
                .claim("state", state == null ? "" : state)
                .claim("code_challenge", codeChallenge == null ? "" : codeChallenge)
                .claim("code_challenge_method", codeChallengeMethod == null ? "" : codeChallengeMethod)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    private static CurrentPrincipal currentPrincipal() {
        return OAuth2EndpointSupport.currentPrincipal();
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String desc) {
        return OAuth2EndpointSupport.error(status, code, desc);
    }
}
