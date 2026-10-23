package com.acme.scaffold.realtime.gateway.auth;

import com.acme.scaffold.realtime.gateway.HandshakeAuthenticator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link HmacTokenAuthenticator} 单测：签发 / 校验 / 过期 / 防重放。 */
@DisplayName("HmacTokenAuthenticator HMAC 短期令牌鉴权")
class HmacTokenAuthenticatorTest {

    private static final String SECRET = "test-secret-123";
    private static final long NOW = 1_000_000L;

    private final HmacTokenAuthenticator auth = new HmacTokenAuthenticator(SECRET);

    @Test
    @DisplayName("合法令牌：签名 + 时效通过，返回 userId")
    void validToken_ok() {
        String token = TokenCodec.mint(SECRET, "42", NOW + 60_000L, NOW);
        HandshakeAuthenticator.AuthResult r = auth.authenticate(token, NOW);
        assertTrue(r.ok());
        assertEquals("42", r.userId());
    }

    @Test
    @DisplayName("过期令牌：exp <= now 拒绝")
    void expiredToken_rejected() {
        String token = TokenCodec.mint(SECRET, "42", NOW - 1, NOW);
        HandshakeAuthenticator.AuthResult r = auth.authenticate(token, NOW);
        assertFalse(r.ok());
    }

    @Test
    @DisplayName("签名被篡改：拒绝")
    void tamperedToken_rejected() {
        String token = TokenCodec.mint(SECRET, "42", NOW + 60_000L, NOW);
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        HandshakeAuthenticator.AuthResult r = auth.authenticate(tampered, NOW);
        assertFalse(r.ok());
    }

    @Test
    @DisplayName("密钥不符：拒绝")
    void wrongSecret_rejected() {
        String token = TokenCodec.mint("other-secret", "42", NOW + 60_000L, NOW);
        HandshakeAuthenticator.AuthResult r = auth.authenticate(token, NOW);
        assertFalse(r.ok());
    }

    @Test
    @DisplayName("iat 超出时钟偏差窗口：疑似重放，拒绝")
    void iatFuture_rejected() {
        // iat 比 now 超前超过默认 60s 偏差窗口
        String token = TokenCodec.mint(SECRET, "42", NOW + 60_000L, NOW + 70_000L);
        HandshakeAuthenticator.AuthResult r = auth.authenticate(token, NOW);
        assertFalse(r.ok());
    }
}
