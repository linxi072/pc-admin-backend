package com.acme.scaffold.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** OAuth2 令牌端点纯函数规则单测（E6）：PKCE S256/白名单、三种 grant 形态、响应体拼装。 */
class OAuth2TokenRulesTest {

    @Test
    void shouldComputeS256ChallengePerRfc7636() {
        // RFC 7636 附录 B 已知向量：verifier → S256 challenge
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", OAuth2TokenRules.s256(verifier));
        assertEquals(43, OAuth2TokenRules.s256(verifier).length());
    }

    @Test
    void shouldSkipPkceWhenChallengeAbsent() {
        assertTrue(OAuth2TokenRules.validatePkce(null, null).isEmpty());
        assertTrue(OAuth2TokenRules.validatePkce("  ", "weird").isEmpty());
    }

    @Test
    void shouldRejectInvalidPkceMethodAndLengthAndCharset() {
        String valid = "a".repeat(43);
        assertEquals(1, OAuth2TokenRules.validatePkce(valid, "MD5").size());
        assertTrue(OAuth2TokenRules.validatePkce(valid, "s256").get(0).contains("S256"));

        assertTrue(OAuth2TokenRules.validatePkce("short", "S256").get(0).contains("43~128"));
        assertTrue(OAuth2TokenRules.validatePkce("a".repeat(129), "S256").get(0).contains("43~128"));
        assertTrue(OAuth2TokenRules.validatePkce("a".repeat(42) + "+", "S256").get(0).contains("base64url"));
        assertTrue(OAuth2TokenRules.validatePkce("a".repeat(42) + "+", "plain").get(0).contains("[A-Za-z0-9"));
        assertTrue(OAuth2TokenRules.validatePkce("a".repeat(43), "S256").isEmpty());
        assertTrue(OAuth2TokenRules.validatePkce("a".repeat(42) + "~", "plain").isEmpty());
    }

    @Test
    void shouldValidateGrantShape() {
        List<String> errors = OAuth2TokenRules.validateGrant(null, null, null);
        assertEquals(3, errors.size());
        assertTrue(OAuth2TokenRules.validateGrant("unknown", "c", "s").get(0).contains("不支持"));
        assertTrue(OAuth2TokenRules.validateGrant("client_credentials", "c", "s").isEmpty());
        for (String grant : OAuth2TokenRules.SUPPORTED_GRANTS) {
            assertTrue(OAuth2TokenRules.validateGrant(grant, "c", "s").isEmpty(), grant);
        }
    }

    @Test
    void shouldValidatePasswordAndAuthorizationCodeGrants() {
        assertTrue(OAuth2TokenRules.validatePasswordGrant("password", "c", "s", "u", "p").isEmpty());
        assertEquals(2, OAuth2TokenRules.validatePasswordGrant("password", "c", "s", null, null).size());
        assertTrue(OAuth2TokenRules.validateAuthorizationCodeGrant(
                "authorization_code", "c", "s", "code", "http://cb").isEmpty());
        assertEquals(1, OAuth2TokenRules.validateAuthorizationCodeGrant(
                "authorization_code", "c", "s", null, null).size());
        // 基础参数缺失时不再叠加子参数错误（首错即 invalid_request）
        assertEquals(3, OAuth2TokenRules.validatePasswordGrant(null, null, null, null, null).size());
    }

    @Test
    void shouldBuildResponseWithOptionalScope() {
        Map<String, Object> body = OAuth2TokenRules.buildResponse("tok", "read write", 3600);
        assertEquals("tok", body.get("access_token"));
        assertEquals("Bearer", body.get("token_type"));
        assertEquals(3600L, body.get("expires_in"));
        assertEquals("read write", body.get("scope"));
        Map<String, Object> noScope = OAuth2TokenRules.buildResponse("tok", "  ", 1);
        assertFalse(noScope.containsKey("scope"));
    }
}
