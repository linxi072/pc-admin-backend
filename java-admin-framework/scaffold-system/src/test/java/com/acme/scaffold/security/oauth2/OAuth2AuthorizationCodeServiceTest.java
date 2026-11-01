package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("OAuth2 授权码签发/兑换")
class OAuth2AuthorizationCodeServiceTest {

    private OAuth2AuthorizationCodeService service() {
        return new OAuth2AuthorizationCodeService(new RsaKeyProvider(new JwtProperties()));
    }

    private OAuth2AuthorizationCodeService.CodeContext ctx() {
        return new OAuth2AuthorizationCodeService.CodeContext(
                42L, "alice", 1, Set.of("system:user:read"), Set.of("ADMIN"), "c1", "read", null, null, null);
    }

    @Test
    @DisplayName("签发后可兑换出原始上下文")
    void issue_and_consume() {
        OAuth2AuthorizationCodeService svc = service();
        String code = svc.issue("c1", null, ctx());
        OAuth2AuthorizationCodeService.CodeContext got = svc.consume(code, "c1", null, null);
        assertEquals(42L, got.userId());
        assertEquals("alice", got.username());
        assertEquals(Set.of("system:user:read"), got.authorities());
    }

    @Test
    @DisplayName("授权码一次性：二次兑换失败")
    void single_use() {
        OAuth2AuthorizationCodeService svc = service();
        String code = svc.issue("c1", null, ctx());
        svc.consume(code, "c1", null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.consume(code, "c1", null, null));
        assertEquals(SecurityErrorCode.OAUTH2_INVALID_GRANT, ex.getErrorCode());
    }

    @Test
    @DisplayName("客户端不匹配 → 兑换失败")
    void client_mismatch() {
        OAuth2AuthorizationCodeService svc = service();
        String code = svc.issue("c1", null, ctx());
        BusinessException ex = assertThrows(BusinessException.class, () -> svc.consume(code, "other", null, null));
        assertEquals(SecurityErrorCode.OAUTH2_INVALID_GRANT, ex.getErrorCode());
    }

    @Test
    @DisplayName("redirect_uri 不匹配 → 兑换失败")
    void redirect_mismatch() {
        OAuth2AuthorizationCodeService svc = service();
        String code = svc.issue("c1", "https://a.example/cb", ctx());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.consume(code, "c1", "https://b.example/cb", null));
        assertEquals(SecurityErrorCode.OAUTH2_INVALID_GRANT, ex.getErrorCode());
    }

    @Test
    @DisplayName("PKCE S256：携带正确 verifier 可兑换")
    void pkce_s256_success() {
        OAuth2AuthorizationCodeService svc = service();
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = OAuth2TokenRules.s256(verifier);
        OAuth2AuthorizationCodeService.CodeContext ctx = new OAuth2AuthorizationCodeService.CodeContext(
                42L, "alice", 1, Set.of(), Set.of(), "c1", "read", null, challenge, "S256");
        String code = svc.issue("c1", null, ctx);
        OAuth2AuthorizationCodeService.CodeContext got = svc.consume(code, "c1", null, verifier);
        assertEquals(42L, got.userId());
    }

    @Test
    @DisplayName("PKCE S256：verifier 不匹配 → 兑换失败")
    void pkce_s256_mismatch() {
        OAuth2AuthorizationCodeService svc = service();
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = OAuth2TokenRules.s256(verifier);
        OAuth2AuthorizationCodeService.CodeContext ctx = new OAuth2AuthorizationCodeService.CodeContext(
                42L, "alice", 1, Set.of(), Set.of(), "c1", "read", null, challenge, "S256");
        String code = svc.issue("c1", null, ctx);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.consume(code, "c1", null, "wrong-verifier"));
        assertEquals(SecurityErrorCode.OAUTH2_INVALID_GRANT, ex.getErrorCode());
    }

    @Test
    @DisplayName("PKCE：授权码绑定挑战但未携带 verifier → 兑换失败")
    void pkce_missing_verifier() {
        OAuth2AuthorizationCodeService svc = service();
        String challenge = OAuth2TokenRules.s256("some-verifier");
        OAuth2AuthorizationCodeService.CodeContext ctx = new OAuth2AuthorizationCodeService.CodeContext(
                42L, "alice", 1, Set.of(), Set.of(), "c1", "read", null, challenge, "S256");
        String code = svc.issue("c1", null, ctx);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> svc.consume(code, "c1", null, null));
        assertEquals(SecurityErrorCode.OAUTH2_INVALID_GRANT, ex.getErrorCode());
    }
}
