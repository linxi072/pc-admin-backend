package com.acme.scaffold.security.oauth2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OAuth2 令牌端点纯函数规则（无副作用、可离线单测）。
 *
 * <p>只负责「请求形态校验」与「响应体拼装」两类确定性逻辑，不触碰数据库/密钥/Spring 上下文，
 * 与既有 {@code *Rules} 纯函数范式保持一致。</p>
 */
public final class OAuth2TokenRules {

    public static final String GRANT_CLIENT_CREDENTIALS = "client_credentials";
    public static final String GRANT_PASSWORD = "password";
    public static final String GRANT_AUTHORIZATION_CODE = "authorization_code";
    public static final List<String> SUPPORTED_GRANTS =
            List.of(GRANT_CLIENT_CREDENTIALS, GRANT_PASSWORD, GRANT_AUTHORIZATION_CODE);

    private OAuth2TokenRules() {
    }

    /**
     * 校验 {@code client_credentials} 授权请求基本形态（grant_type 受支持且 client_id / client_secret 齐备）。
     *
     * @return 错误列表，空列表表示通过；首个错误即对应 OAuth2 错误码 {@code invalid_request}。
     */
    public static List<String> validateGrant(String grantType, String clientId, String clientSecret) {
        List<String> errors = new ArrayList<>();
        if (grantType == null || grantType.isBlank()) {
            errors.add("grant_type 必填");
        } else if (!SUPPORTED_GRANTS.contains(grantType.trim())) {
            errors.add("不支持的 grant_type: " + grantType);
        }
        if (clientId == null || clientId.isBlank()) {
            errors.add("client_id 必填");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            errors.add("client_secret 必填");
        }
        return errors;
    }

    /**
     * 校验 {@code password} 授权请求形态（在 client_credentials 基础上要求 username / password）。
     */
    public static List<String> validatePasswordGrant(String grantType, String clientId, String clientSecret,
                                                     String username, String password) {
        List<String> errors = validateGrant(grantType, clientId, clientSecret);
        if (errors.isEmpty()) {
            if (username == null || username.isBlank()) {
                errors.add("username 必填");
            }
            if (password == null || password.isBlank()) {
                errors.add("password 必填");
            }
        }
        return errors;
    }

    /**
     * 校验 {@code authorization_code} 授权请求形态（在 client_credentials 基础上要求 code）。
     */
    public static List<String> validateAuthorizationCodeGrant(String grantType, String clientId, String clientSecret,
                                                              String code, String redirectUri) {
        List<String> errors = validateGrant(grantType, clientId, clientSecret);
        if (errors.isEmpty()) {
            if (code == null || code.isBlank()) {
                errors.add("code 必填");
            }
        }
        return errors;
    }

    /**
     * 构造令牌响应体（OAuth2 标准字段）。scope 以空格分隔，符合 RFC 6749 §5.1。
     */
    public static Map<String, Object> buildResponse(String accessToken, String scope, long expiresInSeconds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("access_token", accessToken);
        body.put("token_type", "Bearer");
        body.put("expires_in", expiresInSeconds);
        if (scope != null && !scope.isBlank()) {
            body.put("scope", scope);
        }
        return body;
    }
}
