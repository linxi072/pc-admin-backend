package com.acme.scaffold.security.oauth2;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("OAuth2 令牌规则（纯函数）")
class OAuth2TokenRulesTest {

    @Test
    @DisplayName("合法 client_credentials 请求通过校验")
    void validateGrant_valid() {
        List<String> errors = OAuth2TokenRules.validateGrant(
                OAuth2TokenRules.GRANT_CLIENT_CREDENTIALS, "app-12345678", "secret");
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("缺少参数或不受支持的 grant 均报错")
    void validateGrant_invalid() {
        assertFalse(OAuth2TokenRules.validateGrant(null, "id", "pw").isEmpty());
        assertFalse(OAuth2TokenRules.validateGrant("password", "id", "pw").isEmpty());
        assertFalse(OAuth2TokenRules.validateGrant("client_credentials", null, "pw").isEmpty());
        assertFalse(OAuth2TokenRules.validateGrant("client_credentials", "id", null).isEmpty());
    }

    @Test
    @DisplayName("响应体结构符合 OAuth2 标准")
    void buildResponse_structure() {
        Map<String, Object> body = OAuth2TokenRules.buildResponse("tok", "read write", 1800);
        assertEquals("tok", body.get("access_token"));
        assertEquals("Bearer", body.get("token_type"));
        assertEquals(1800L, body.get("expires_in"));
        assertEquals("read write", body.get("scope"));
    }

    @Test
    @DisplayName("scope 为空时不输出 scope 字段")
    void buildResponse_noScope() {
        Map<String, Object> body = OAuth2TokenRules.buildResponse("tok", "", 1800);
        assertFalse(body.containsKey("scope"));
    }
}
