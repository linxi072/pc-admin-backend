package com.acme.scaffold.system.notification.unsubscribe.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.system.notification.unsubscribe.domain.rule.UnsubscribeTokenRules.UnsubscribeTarget;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 退订令牌纯函数规则单测（M15）：铸造/验签往返、篡改、过期与形态边界。 */
class UnsubscribeTokenRulesTest {

    private static final String SECRET = "unit-test-unsubscribe-secret";

    @Test
    void shouldMintAndVerifyRoundTrip() {
        long exp = 2_000L;
        String token = UnsubscribeTokenRules.mint(SECRET, 42L, Channel.EMAIL, "ALERT", exp);
        Optional<UnsubscribeTarget> parsed = UnsubscribeTokenRules.verify(SECRET, token, 1_000L);
        assertTrue(parsed.isPresent());
        assertEquals(42L, parsed.get().userId());
        assertEquals(Channel.EMAIL, parsed.get().channel());
        assertEquals("ALERT", parsed.get().msgType());
        assertEquals(exp, parsed.get().expMillis());
    }

    @Test
    void shouldMintDifferentTokensForSameTarget() {
        String first = UnsubscribeTokenRules.mint(SECRET, 42L, Channel.SMS, "SYSTEM", 9_999L);
        String second = UnsubscribeTokenRules.mint(SECRET, 42L, Channel.SMS, "SYSTEM", 9_999L);
        assertNotEquals(first, second); // nonce 令链接不可关联
    }

    @Test
    void shouldRejectTamperedTokenOrWrongSecret() {
        String token = UnsubscribeTokenRules.mint(SECRET, 7L, Channel.EMAIL, "ALERT", 5_000L);
        assertTrue(UnsubscribeTokenRules.verify(SECRET, token, 1_000L).isPresent());
        assertTrue(UnsubscribeTokenRules.verify("wrong-secret", token, 1_000L).isEmpty());

        // 篡改 payload 段（重签一个不同 userId 的 payload，签名段保持原样）
        String[] parts = token.split("\\.");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("8|EMAIL|ALERT|5000|nonce123".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];
        assertTrue(UnsubscribeTokenRules.verify(SECRET, tampered, 1_000L).isEmpty());

        // 篡改版本段
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "v2." + parts[1] + "." + parts[2], 1_000L).isEmpty());
    }

    @Test
    void shouldRejectExpiredToken() {
        String token = UnsubscribeTokenRules.mint(SECRET, 1L, Channel.IN_APP, "MSG", 1_000L);
        assertTrue(UnsubscribeTokenRules.verify(SECRET, token, 999L).isPresent());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, token, 1_000L).isEmpty()); // exp <= now 即过期
        assertTrue(UnsubscribeTokenRules.verify(SECRET, token, 2_000L).isEmpty());
    }

    @Test
    void shouldRejectMalformedTokensAndBlankInputs() {
        assertTrue(UnsubscribeTokenRules.verify(null, "x", 0L).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(" ", "x", 0L).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, null, 0L).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "  ", 0L).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "not-a-token", 0L).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "v1.only.two", 0L).isEmpty());
        // 非法 base64 payload（签名无法匹配，先被验签拒绝）
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "v1.@@@.sig", 0L).isEmpty());
    }

    @Test
    void shouldRejectBadPayloadSemantics() {
        // 伪造签名正确的 token：payload 段数不足 / 数字或枚举非法 / msgType 空白
        String fewFields = forged("1|EMAIL|ALERT");
        assertTrue(UnsubscribeTokenRules.verify(SECRET, fewFields, 0L).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, forged("x|EMAIL|ALERT|5000|n"), 0L).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, forged("1|NO_SUCH_CHANNEL|ALERT|5000|n"), 0L).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, forged("1|EMAIL|  |5000|n"), 0L).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, forged("1|EMAIL|ALERT|bad|n"), 0L).isEmpty());
    }

    /** 构造签名正确但 payload 语义非法的 token（用于单独验证语义解析分支）。 */
    private static String forged(String payload) {
        String body = "v1." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // 复用规则自身的 sign 口径：mint 出的签名段对同一 body 有效，这里直接取一个已知密钥签出的签名
        // 通过 mint 的签名算法不可直接调用（private），故用相同 HMAC 逻辑本地计算。
        String sig;
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(
                    SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            sig = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        return body + "." + sig;
    }
}
