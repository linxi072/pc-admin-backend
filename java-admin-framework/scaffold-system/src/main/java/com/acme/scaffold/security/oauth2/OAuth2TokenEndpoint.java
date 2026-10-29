package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.system.openapi.OpenApiClientService;
import com.acme.scaffold.system.openapi.domain.OpenApiClientDO;
import com.acme.scaffold.system.openapi.rules.OpenApiClientRules;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * 客户端认证复用 {@link OpenApiClientService#authenticateClient}（BCrypt 密钥哈希 + 状态/过期校验），
 * 令牌签发委托 {@link OAuth2TokenProvider}。</p>
 *
 * <p>端点本身匿名可访问（已加入 {@code SecurityConfig#BASE_PUBLIC_API}），但实际访问受 client 认证保护；
 * 失败响应遵循 OAuth2 错误码（{@code invalid_request} / {@code invalid_client} / {@code invalid_grant}）。</p>
 */
@RestController
@RequestMapping("/oauth2")
public class OAuth2TokenEndpoint {

    private final OpenApiClientService clientService;
    private final OAuth2TokenProvider tokenProvider;
    private final JwtProperties properties;
    private final OAuth2UserAuthenticator userAuthenticator;
    private final OAuth2AuthorizationCodeService codeService;

    public OAuth2TokenEndpoint(OpenApiClientService clientService,
                               OAuth2TokenProvider tokenProvider,
                               JwtProperties properties,
                               OAuth2UserAuthenticator userAuthenticator,
                               OAuth2AuthorizationCodeService codeService) {
        this.clientService = clientService;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
        this.userAuthenticator = userAuthenticator;
        this.codeService = codeService;
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

        OpenApiClientDO client;
        try {
            client = clientService.authenticateClient(clientId, clientSecret);
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

    private ResponseEntity<Map<String, Object>> clientCredentialsGrant(OpenApiClientDO client, long ttlSeconds) {
        Set<String> scopes = OpenApiClientRules.parseScopes(client.getScopes());
        String accessToken = tokenProvider.issueClientCredentialsToken(client.getClientId(), scopes);
        String scopeStr = scopes.isEmpty() ? "" : scopes.stream().sorted().collect(Collectors.joining(" "));
        return ok(accessToken, scopeStr, ttlSeconds);
    }

    private ResponseEntity<Map<String, Object>> passwordGrant(OpenApiClientDO client, String username,
                                                             String password, String scope, long ttlSeconds) {
        OAuth2UserAuthenticator.AuthenticatedUser user = userAuthenticator.authenticate(username, password);
        Set<String> granted = resolveScopes(client.getScopes(), scope);
        String accessToken = tokenProvider.issueUserToken(client.getClientId(), user.userId(), user.username(),
                user.tokenVersion(), granted, user.authorities());
        String scopeStr = granted.isEmpty() ? "" : granted.stream().sorted().collect(Collectors.joining(" "));
        return ok(accessToken, scopeStr, ttlSeconds);
    }

    private ResponseEntity<Map<String, Object>> authorizationCodeGrant(OpenApiClientDO client, String code,
                                                                     String redirectUri, long ttlSeconds) {
        OAuth2AuthorizationCodeService.CodeContext ctx = codeService.consume(code, client.getClientId(), redirectUri);
        Set<String> granted = (ctx.scope() == null || ctx.scope().isBlank())
                ? OpenApiClientRules.parseScopes(client.getScopes())
                : OpenApiClientRules.parseScopes(ctx.scope());
        String accessToken = tokenProvider.issueUserToken(client.getClientId(), ctx.userId(), ctx.username(),
                ctx.tokenVersion(), granted, ctx.authorities());
        String scopeStr = granted.isEmpty() ? "" : granted.stream().sorted().collect(Collectors.joining(" "));
        return ok(accessToken, scopeStr, ttlSeconds);
    }

    /** 请求的 scope 与客户端授权范围取交集；未请求 scope 时回落为客户端全量授权范围。 */
    private static Set<String> resolveScopes(String clientScopes, String requestedScope) {
        Set<String> allowed = OpenApiClientRules.parseScopes(clientScopes);
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
