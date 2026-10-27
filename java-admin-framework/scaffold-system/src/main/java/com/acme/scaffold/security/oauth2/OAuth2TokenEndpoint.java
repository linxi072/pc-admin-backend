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

/**
 * 协议级 OAuth2 令牌端点（{@code POST /oauth2/token}）。
 *
 * <p>支持 {@code client_credentials} 授权模式：客户端凭 client_id/client_secret（表单参数或
 * Basic 认证头）换取 RS256 签名的访问令牌。客户端认证复用
 * {@link OpenApiClientService#authenticateClient}（BCrypt 密钥哈希 + 状态/过期校验），
 * 令牌签发委托 {@link OAuth2TokenProvider}。</p>
 *
 * <p>端点本身匿名可访问（已加入 {@code SecurityConfig#BASE_PUBLIC_API}），但实际访问受 client 认证保护；
 * 失败响应遵循 OAuth2 错误码（{@code invalid_request} / {@code invalid_client}）。</p>
 */
@RestController
@RequestMapping("/oauth2")
public class OAuth2TokenEndpoint {

    private final OpenApiClientService clientService;
    private final OAuth2TokenProvider tokenProvider;
    private final JwtProperties properties;

    public OAuth2TokenEndpoint(OpenApiClientService clientService,
                               OAuth2TokenProvider tokenProvider,
                               JwtProperties properties) {
        this.clientService = clientService;
        this.tokenProvider = tokenProvider;
        this.properties = properties;
    }

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(name = "grant_type", required = false) String grantType,
            @RequestParam(name = "client_id", required = false) String clientId,
            @RequestParam(name = "client_secret", required = false) String clientSecret,
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

        List<String> errors = OAuth2TokenRules.validateGrant(grantType, clientId, clientSecret);
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

        Set<String> scopes = OpenApiClientRules.parseScopes(client.getScopes());
        String accessToken = tokenProvider.issueClientCredentialsToken(client.getClientId(), scopes);
        String scopeStr = String.join(" ", scopes);
        Map<String, Object> body = OAuth2TokenRules.buildResponse(
                accessToken, scopeStr, properties.getOauth2TokenTtl().getSeconds());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String desc) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("error_description", desc);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
