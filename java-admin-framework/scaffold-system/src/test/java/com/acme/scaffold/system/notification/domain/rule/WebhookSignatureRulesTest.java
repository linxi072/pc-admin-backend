package com.acme.scaffold.system.notification.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Webhook 外发签名纯函数规则单测（M18）：签名往返、请求头契约、时间戳窗口与参数边界。 */
class WebhookSignatureRulesTest {

    private static final String KEY = "unit-test-webhook-secret";
    private static final String BODY = "{\"event\":\"ALERT\",\"value\":42}";

    @Test
    void shouldSignAndVerifyRoundTrip() {
        long ts = 1_000L;
        String nonce = "nonce-abc";
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        assertTrue(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce,
                WebhookSignatureRules.buildHeader(sig), BODY, 300_000L, 2_000L));
        // 裸签名（无方案前缀）亦可验签
        assertTrue(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce,
                sig, BODY, 300_000L, 2_000L));
    }

    @Test
    void shouldBuildAndStripSchemeHeader() {
        String sig = WebhookSignatureRules.sign(KEY, 1L, "n", BODY);
        assertEquals(WebhookSignatureRules.SCHEME + sig, WebhookSignatureRules.buildHeader(sig));
        assertEquals(sig, WebhookSignatureRules.stripScheme(WebhookSignatureRules.buildHeader(sig)));
        assertEquals(sig, WebhookSignatureRules.stripScheme(sig)); // 无前缀原样返回
        assertEquals("", WebhookSignatureRules.stripScheme(null));
    }

    @Test
    void shouldGenerateUniqueNonce() {
        assertNotEquals(WebhookSignatureRules.newNonce(), WebhookSignatureRules.newNonce());
    }

    @Test
    void shouldRejectTamperedBodyNonceOrKey() {
        long ts = 1_000L;
        String nonce = "nonce-abc";
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        String header = WebhookSignatureRules.buildHeader(sig);

        assertFalse(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce, header,
                BODY + " ", 300_000L, 2_000L)); // 报文被改
        assertFalse(WebhookSignatureRules.verify(KEY, String.valueOf(ts), "other-nonce", header,
                BODY, 300_000L, 2_000L)); // nonce 被改
        assertFalse(WebhookSignatureRules.verify("wrong-key", String.valueOf(ts), nonce, header,
                BODY, 300_000L, 2_000L)); // 密钥不符
        assertFalse(WebhookSignatureRules.verify(KEY, "999", nonce, header,
                BODY, 300_000L, 2_000L)); // 时间戳参与签名，被改即失败
    }

    @Test
    void shouldEnforceTimestampFreshnessWindow() {
        long now = 1_000_000L;
        long ts = now - 300_000L;
        String nonce = "n";
        String sig = WebhookSignatureRules.sign(KEY, ts, nonce, BODY);
        String header = WebhookSignatureRules.buildHeader(sig);

        assertTrue(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce, header, BODY, 300_000L, now));
        // 过期（超过 maxAge）
        assertFalse(WebhookSignatureRules.verify(KEY, String.valueOf(ts), nonce, header, BODY, 299_999L, now));
        // 未来时钟漂移超过 5s 容错
        long futureTs = now + 5_001L;
        String futureSig = WebhookSignatureRules.sign(KEY, futureTs, nonce, BODY);
        assertFalse(WebhookSignatureRules.verify(KEY, String.valueOf(futureTs), nonce,
                WebhookSignatureRules.buildHeader(futureSig), BODY, 300_000L, now));
        // 5s 内正向漂移允许
        long nearFutureTs = now + 5_000L;
        String nearSig = WebhookSignatureRules.sign(KEY, nearFutureTs, nonce, BODY);
        assertTrue(WebhookSignatureRules.verify(KEY, String.valueOf(nearFutureTs), nonce,
                WebhookSignatureRules.buildHeader(nearSig), BODY, 300_000L, now));
    }

    @Test
    void shouldRejectBlankAndMalformedInputs() {
        String sig = WebhookSignatureRules.sign(KEY, 1_000L, "n", BODY);
        assertFalse(WebhookSignatureRules.verify(null, "1000", "n", sig, BODY, 300_000L, 2_000L));
        assertFalse(WebhookSignatureRules.verify(" ", "1000", "n", sig, BODY, 300_000L, 2_000L));
        assertFalse(WebhookSignatureRules.verify(KEY, null, "n", sig, BODY, 300_000L, 2_000L));
        assertFalse(WebhookSignatureRules.verify(KEY, " ", "n", sig, BODY, 300_000L, 2_000L));
        assertFalse(WebhookSignatureRules.verify(KEY, "1000", null, sig, BODY, 300_000L, 2_000L));
        assertFalse(WebhookSignatureRules.verify(KEY, "1000", "n", null, BODY, 300_000L, 2_000L));
        assertFalse(WebhookSignatureRules.verify(KEY, "1000", "n", sig, null, 300_000L, 2_000L));
        assertFalse(WebhookSignatureRules.verify(KEY, "not-a-number", "n", sig, BODY, 300_000L, 2_000L));
    }
}
