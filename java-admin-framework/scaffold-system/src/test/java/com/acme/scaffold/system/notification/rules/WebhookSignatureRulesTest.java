package com.acme.scaffold.system.notification.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link WebhookSignatureRules} 纯函数测试：签名确定性、验签命中/篡改/错密钥/过期/
 * 缺参/未来时钟漂移/nonce 唯一性等分支全覆盖。不依赖 Spring 与网络。
 */
class WebhookSignatureRulesTest {

    private static final String KEY = "test-signing-key";
    private static final String BODY = "{\"title\":\"t\",\"content\":\"c\",\"msgType\":\"ALERT\"}";

    @Test
    void signIsDeterministicAndNonBlank() {
        String s1 = WebhookSignatureRules.sign(KEY, 1_700_000_000_000L, "nonceA", BODY);
        String s2 = WebhookSignatureRules.sign(KEY, 1_700_000_000_000L, "nonceA", BODY);
        assertNotNull(s1);
        assertFalse(s1.isBlank());
        assertEquals(s1, s2);
    }

    @Test
    void verifySucceedsForMatchingInputsWithSchemePrefix() {
        long ts = 1_700_000_000_000L;
        String nonce = WebhookSignatureRules.newNonce();
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        assertTrue(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce,
                WebhookSignatureRules.buildHeader(sig), BODY, 60_000, ts + 1_000));
    }

    @Test
    void verifySucceedsForBareSignatureWithoutScheme() {
        long ts = 1_700_000_000_000L;
        String nonce = WebhookSignatureRules.newNonce();
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        assertTrue(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce, sig, BODY, 60_000, ts + 1_000));
    }

    @Test
    void verifyFailsWhenBodyTampered() {
        long ts = 1_700_000_000_000L;
        String nonce = WebhookSignatureRules.newNonce();
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        assertFalse(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce,
                WebhookSignatureRules.buildHeader(sig), BODY + "x", 60_000, ts + 1_000));
    }

    @Test
    void verifyFailsWhenKeyWrong() {
        long ts = 1_700_000_000_000L;
        String nonce = WebhookSignatureRules.newNonce();
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        assertFalse(WebhookSignatureRules.verify("wrong-key", String.valueOf(ts), nonce,
                WebhookSignatureRules.buildHeader(sig), BODY, 60_000, ts + 1_000));
    }

    @Test
    void verifyFailsWhenTimestampStaleBeyondMaxAge() {
        long ts = 1_700_000_000_000L;
        String nonce = WebhookSignatureRules.newNonce();
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        // now 远超 maxAge
        assertFalse(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce,
                WebhookSignatureRules.buildHeader(sig), BODY, 60_000, ts + 10_000_000));
    }

    @Test
    void verifyRejectsFutureClockSkewBeyondTolerance() {
        long ts = 1_700_000_000_000L;
        String nonce = WebhookSignatureRules.newNonce();
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        // now 相对 ts 大幅落后（ts 被预置到很久以后）→ 拒绝
        assertFalse(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce,
                WebhookSignatureRules.buildHeader(sig), BODY, 60_000, ts - 100_000));
    }

    @Test
    void verifyFailsOnMissingOrBlankParams() {
        assertFalse(WebhookSignatureRules.verify(null, "1", "n", "s", BODY, 60_000, 1));
        assertFalse(WebhookSignatureRules.verify(KEY, null, "n", "s", BODY, 60_000, 1));
        assertFalse(WebhookSignatureRules.verify(KEY, "1", "n", "", BODY, 60_000, 1));
        assertFalse(WebhookSignatureRules.verify(KEY, "1", "n", "s", null, 60_000, 1));
    }

    @Test
    void verifyFailsOnNonNumericTimestamp() {
        long ts = 1_700_000_000_000L;
        String nonce = WebhookSignatureRules.newNonce();
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        assertFalse(WebhookSignatureRules.verify(KEY, "not-a-number", nonce,
                WebhookSignatureRules.buildHeader(sig), BODY, 60_000, ts + 1_000));
    }

    @Test
    void differentNonceYieldsDifferentSignature() {
        long ts = 1_700_000_000_000L;
        String s1 = WebhookSignatureRules.sign(KEY, ts, "nonce1", BODY);
        String s2 = WebhookSignatureRules.sign(KEY, ts, "nonce2", BODY);
        assertNotEquals(s1, s2);
    }

    @Test
    void newNonceProducesUniqueNonBlankValues() {
        String a = WebhookSignatureRules.newNonce();
        String b = WebhookSignatureRules.newNonce();
        assertFalse(a.isBlank());
        assertFalse(b.isBlank());
        assertNotEquals(a, b);
    }
}
