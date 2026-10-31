package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.oauth2.OpenApiClientRules;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.oauth2.OAuth2ClientDataPort;
import com.acme.scaffold.security.oauth2.OAuth2ClientView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 协议级 OAuth2 令牌端点（{@code POST /oauth2/token}）。
 *
 * <p>支持三种授权模式：
 * <ul>
 *   <li>{@code client_credentials}：客户端凭 client_id/client_secret 换取 RS256 签名的<b>客户端令牌</b>
 *       （{@code userId=null}，仅承载 scope）；</li>
 *   <li>{@code password}：客户端凭证 + 资源拥有者用户名/密码，换取<b>代表用户</b>的令牌
 *       （携带用户 authorities，可访问 {@code system:*} 受保护接口）；</li>
 *   <li>{@code authorization_code}：客户端凭证 + 一次性授权码，换取<b>代表用户</b>的令牌。</li>
 * </ul>
 * 客户端认证复用 {@link OAuth2ClientDataPort#authenticate}（BCrypt 密钥哈希 + 状态/过期校验，由 system 适配器委托 OpenApiClientService），
 * 令牌签发委托 {@link OAuth2TokenProvider}。</p>
 *
 * <p>端点本身匿名可访问（已加入 {@code SecurityConfig#BASE_PUBLIC_API}），但实际访问受 client 认证保护；
 * 失败响应遵循 OAuth2 错误码（{@code invalid_request} / {@code invalid_client} / {@code invalid_grant}）。</p>
 */
@RestController
@RequestMapping("/oauth2")
@Slf4j
public class OAuth2TokenEndpoint {

    private final OAuth2ClientDataPort clientPort;
    private final OAuth2TokenProvider tokenProvider;
    private final JwtProperties properties;
    private final OAuth2UserAuthenticator userAuthenticator;
    private final OAuth2AuthorizationCodeService codeService;
    private final OAuth2TokenRegistry tokenRegistry;

    public OAuth2TokenEndpoint(OAuth2ClientDataPort clientPort,
                               OAuth2TokenProvider tokenProvider,
                               JwtProperties properties,
                               OAuth2UserAuthenticator userAuthenticator,
                               OAuth2AuthorizationCodeService codeService,
                               OAuth2TokenRegistry tokenRegistry) {
        this.clientPort = clientPort;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.userAuthenticator = userAuthenticator;
        this.codeService = codeService;
        this.tokenRegistry = tokenRegistry;
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret,
            @RequestParam(name = "username", required = false) String username,
            @RequestParam(name = "password", required = false) String password,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "redirect_uri", required = false) String redirectUri,
            @RequestParam(name = "scope", required = false) String scope,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        // Basic 认证头优先（RFC 6749 §2.3.1）
        if (authorization != null && authorization.startsWith("Basic ")) {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)),
                    StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (idx >= 0) {
                clientId = decoded.substring(0, idx);
                clientSecret = decoded.substring(idx + 1);
            }
        }

        // 按授权模式分别校验请求形态
        List<String> errors;
        if (OAuth2TokenRules.GRANT_PASSWORD.equals(grantType)) {
            errors = OAuth2TokenRules.validatePasswordGrant(grantType, clientId, clientSecret, username, password);
        } else if (OAuth2TokenRules.GRANT_AUTHORIZATION_CODE.equals(grantType)) {
            errors = OAuth2TokenRules.validateAuthorizationCodeGrant(grantType, clientId, clientSecret, code, redirectUri);
        } else {
            errors = OAuth2TokenRules.validateGrant(grantType, clientId, clientSecret);
        }
        if (!errors.isEmpty()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_request", String.join("；", errors));
        }

        OAuth2ClientView client;
        try {
            client = clientPort.authenticate(clientId, clientSecret);
        } catch (BusinessException e) {
            // 统一以 invalid_client 响应，避免泄露具体失败原因（不存在/停用/过期/密钥错误）
            return error(HttpStatus.UNAUTHORIZED, "invalid_client", "客户端认证失败");
        }

        long ttlSeconds = properties.getOauth2TokenTtl().getSeconds();
        try {
            if (OAuth2TokenRules.GRANT_PASSWORD.equals(grantType)) {
                return passwordGrant(client, username, password, scope, ttlSeconds);
            } else if (OAuth2TokenRules.GRANT_AUTHORIZATION_CODE.equals(grantType)) {
                return authorizationCodeGrant(client, code, redirectUri, ttlSeconds);
            } else {
                return clientCredentialsGrant(client, ttlSeconds);
            }
        } catch (BusinessException e) {
            return error(HttpStatus.BAD_REQUEST, "invalid_grant", e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> clientCredentialsGrant(OAuth2ClientView client, long ttlSeconds) {
        Set<String> scopes = client.scopes();
        String jti = UUID.randomUUID().toString();
        String accessToken = tokenProvider.issueClientCredentialsToken(client.clientId(), scopes, jti);
        String scopeStr = scopes.isEmpty() ? "" : scopes.stream().sorted().collect(Collectors.joining(" "));
        recordIssuance(jti, client.clientId(), null, null, scopes, "CLIENT", ttlSeconds);
        return ok(accessToken, scopeStr, ttlSeconds);
    }

    private ResponseEntity<Map<String, Object>> passwordGrant(OAuth2ClientView client, String username,
                                                             String password, String scope, long ttlSeconds) {
        OAuth2UserAuthenticator.AuthenticatedUser user = userAuthenticator.authenticate(username, password);
        Set<String> granted = resolveScopes(client.scopes(), scope);
        String jti = UUID.randomUUID().toString();
        String accessToken = tokenProvider.issueUserToken(client.clientId(), user.userId(), user.username(),
                user.tokenVersion(), granted, user.authorities(), jti);
        String scopeStr = granted.isEmpty() ? "" : granted.stream().sorted().collect(Collectors.joining(" "));
        recordIssuance(jti, client.clientId(), user.userId(), user.username(), granted, "USER", ttlSeconds);
        return ok(accessToken, scopeStr, ttlSeconds);
    }

    private ResponseEntity<Map<String, Object>> authorizationCodeGrant(OAuth2ClientView client, String code,
                                                                     String redirectUri, long ttlSeconds) {
        OAuth2AuthorizationCodeService.CodeContext ctx = codeService.consume(code, client.clientId(), redirectUri);
        Set<String> granted = (ctx.scope() == null || ctx.scope().isBlank())
                ? client.scopes()
                : OpenApiClientRules.parseScopes(ctx.scope());
        String jti = UUID.randomUUID().toString();
        String accessToken = tokenProvider.issueUserToken(client.clientId(), ctx.userId(), ctx.username(),
                ctx.tokenVersion(), granted, ctx.authorities(), jti);
        String scopeStr = granted.isEmpty() ? "" : granted.stream().sorted().collect(Collectors.joining(" "));
        recordIssuance(jti, client.clientId(), ctx.userId(), ctx.username(), granted, "USER", ttlSeconds);
        return ok(accessToken, scopeStr, ttlSeconds);
    }

    /**
     * 签发后落库注册表（M43 令牌治理）。失败仅记录告警并降级放行——注册表不可用不应阻断令牌签发，
     * 代价是丢失该令牌的可观测与可吊销能力，但令牌本身依然有效（与「基础设施故障不应导致全站不可用」一致）。
     */
    private void recordIssuance(String jti, String clientId, Long userId, String username,
                               Set<String> scopes, String tokenType, long ttlSeconds) {
        try {
            LocalDateTime now = LocalDateTime.now();
            tokenRegistry.recordIssuance(jti, clientId, userId, username, scopes, tokenType, now, now.plusSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("OAuth2 令牌签发落库失败（已降级放行）jti={} clientId={}", jti, clientId, e);
        }
    }

    /** 请求的 scope 与客户端授权范围取交集；未请求 scope 时回落为客户端全量授权范围。 */
    private static Set<String> resolveScopes(Set<String> allowed, String requestedScope) {
        if (requestedScope == null || requestedScope.isBlank()) {
            return allowed;
        }
        Set<String> requested = OpenApiClientRules.parseScopes(requestedScope);
        return requested.stream().filter(allowed::contains).collect(Collectors.toSet());
    }

    private static ResponseEntity<Map<String, Object>> ok(String accessToken, String scopeStr, long ttlSeconds) {
        Map<String, Object> body = OAuth2TokenRules.buildResponse(accessToken, scopeStr, ttlSeconds);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String desc) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("error_description", desc);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
