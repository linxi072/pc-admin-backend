package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.system.openapi.OpenApiClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth2 令牌自省端点（RFC 7662 {@code POST /oauth2/introspect}）。
 *
 * <p>调用方须认证：携带合法 Bearer 令牌（自身 token）或 {@code client_id/client_secret}
 * （Basic 头）。端点本身加入 {@code SecurityConfig#BASE_PUBLIC_API}（匿名可到达），
 * 由本控制器内部完成调用方鉴权，与 {@code /oauth2/token} 同口径。</p>
 *
 * <p>被自省的 {@code token} 经复合解码器（HS256 + RS256）解码；解码成功且未过期即
 * {@code active=true} 并附带声明，否则返回 {@code {"active":false}}（RFC 要求自省失败返回 200 + active=false，
 * 而非 401）。</p>
 */
@RestController
@RequestMapping("/oauth2")
public class OAuth2IntrospectionEndpoint {

    private final JwtDecoder decoder;
    private final OpenApiClientService clientService;

    public OAuth2IntrospectionEndpoint(JwtDecoder decoder, OpenApiClientService clientService) {
        this.decoder = decoder;
        this.clientService = clientService;
    }

    @PostMapping(value = "/introspect", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> introspect(
            @RequestParam(name = "token", required = false) String token,
            @RequestHeader(value = "Authorization", required = false) String authorization) {

        if (!callerAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "invalid_client", "error_description", "调用方未认证"));
        }

        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", "invalid_request", "error_description", "token 必填"));
        }

        try {
            Jwt jwt = decoder.decode(token);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(OAuth2IntrospectRules.buildResponse(jwt));
        } catch (JwtException e) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body(OAuth2IntrospectRules.inactive());
        }
    }

    /** 调用方鉴权：Bearer 自身令牌（解码成功）或 Basic 客户端凭证（认证通过）。 */
    private boolean callerAuthorized(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return false;
        }
        if (authorization.startsWith("Bearer ")) {
            try {
                decoder.decode(authorization.substring(7).trim());
                return true;
            } catch (JwtException e) {
                return false;
            }
        }
        if (authorization.startsWith("Basic ")) {
            String decoded = new String(Base64.getDecoder().decode(authorization.substring(6)),
                    StandardCharsets.UTF_8);
            int idx = decoded.indexOf(':');
            if (idx < 0) {
                return false;
            }
            try {
                clientService.authenticateClient(decoded.substring(0, idx), decoded.substring(idx + 1));
                return true;
            } catch (BusinessException e) {
                return false;
            }
        }
        return false;
    }
}
