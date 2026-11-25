package com.acme.scaffold.realtime.gateway.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.realtime.gateway.auth.TokenCodec.ParsedToken;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 短期令牌编解码单测：往返、篡改拒绝、畸形输入容错与时效校验职责边界。 */
class TokenCodecTest {

    private static final String SECRET = "gateway-demo-secret";

    @Test
    void mintedTokenShouldRoundTrip() {
        String token = TokenCodec.mint(SECRET, "user-7", 9_000L, 1_000L);
        Optional<ParsedToken> parsed = TokenCodec.parse(token);
        assertTrue(parsed.isPresent());
        assertEquals("user-7", parsed.get().userId());
        assertEquals(9_000L, parsed.get().exp());
        assertEquals(1_000L, parsed.get().iat());
        assertTrue(TokenCodec.verify(SECRET, token));
    }

    @Test
    void tamperedPayloadShouldFailVerification() {
        String token = TokenCodec.mint(SECRET, "user-7", 9_000L, 1_000L);
        assertFalse(TokenCodec.verify(SECRET, token.replace("user-7", "user-8")));
        assertFalse(TokenCodec.verify(SECRET, token.replace("9000", "9999")));
    }

    @Test
    void wrongSecretShouldFailVerification() {
        String token = TokenCodec.mint(SECRET, "user-7", 9_000L, 1_000L);
        assertFalse(TokenCodec.verify("other-secret", token));
    }

    @Test
    void malformedTokensShouldParseEmptyAndFailVerification() {
        for (String bad : new String[] {null, "", "  ", "v1.only.three", "v2.u.9.1.sig", "v1.u.NaN.1.sig"}) {
            assertTrue(TokenCodec.parse(bad).isEmpty(), "输入应被安全拒绝: " + bad);
            assertFalse(TokenCodec.verify(SECRET, bad));
        }
    }

    @Test
    void verifyChecksSignatureOnlyExpiryIsCallerResponsibility() {
        String expired = TokenCodec.mint(SECRET, "user-7", 1L, 0L);
        assertTrue(TokenCodec.verify(SECRET, expired), "verify 仅验签，时效由调用方按 exp 判定");
        assertTrue(TokenCodec.parse(expired).get().exp() < System.currentTimeMillis());
    }
}
