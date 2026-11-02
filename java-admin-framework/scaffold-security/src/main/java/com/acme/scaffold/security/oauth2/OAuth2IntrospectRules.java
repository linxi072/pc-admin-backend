package com.acme.scaffold.security.oauth2;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OAuth2 令牌自省（RFC 7662）响应纯函数规则。
 *
 * <p>仅负责把已解码的 {@link Jwt} 映射为自省响应体，不触碰密钥/数据库/Spring 上下文，
 * 与既有 {@code *Rules} 纯函数范式一致，可离线单测。</p>
 */
public final class OAuth2IntrospectRules {

    private OAuth2IntrospectRules() {
    }

    /** 活跃令牌响应：active=true 并附带标准声明。 */
    public static Map<String, Object> buildResponse(Jwt jwt) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", true);
        m.put("token_type", "Bearer");
        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            m.put("scope", scope);
        }
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId != null) {
            m.put("client_id", clientId);
        }
        String username = jwt.getClaimAsString("username");
        if (username == null) {
            username = jwt.getSubject();
        }
        if (username != null) {
            m.put("username", username);
        }
        String sub = jwt.getSubject();
        if (sub != null) {
            m.put("sub", sub);
        }
        String aud = jwt.getClaimAsString("aud");
        if (aud != null) {
            m.put("aud", aud);
        }
        String iss = jwt.getClaimAsString("iss");
        if (iss != null) {
            m.put("iss", iss);
        }
        if (jwt.getExpiresAt() != null) {
            m.put("exp", jwt.getExpiresAt().getEpochSecond());
        }
        if (jwt.getIssuedAt() != null) {
            m.put("iat", jwt.getIssuedAt().getEpochSecond());
        }
        String authorities = jwt.getClaimAsString("authorities");
        if (authorities != null && !authorities.isBlank()) {
            m.put("authorities", authorities);
        }
        return m;
    }

    /** 失效/非法令牌响应。 */
    public static Map<String, Object> inactive() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", false);
        return m;
    }
}
