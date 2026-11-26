package com.acme.scaffold.system.notification.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 机器人加签规则单测：共享 HMAC 原语与企业微信/钉钉 query 参数的 URL 编码契约。 */
class RobotSigningRulesTest {

    private static final String SECRET = "test-secret";
    private static final long TIMESTAMP = 1_700_000_000_000L;
    // 该摘要刻意包含 + / =：裸 base64 放进 query 时 '+' 会被按表单语义解码为空格。
    private static final String RAW_SIGN = "BYMqUCZnSqbfPf1GCfZftO7Rg2g6P+Rp3/4+bLNtSGA=";
    private static final String ENCODED_SIGN = "BYMqUCZnSqbfPf1GCfZftO7Rg2g6P%2BRp3%2F4%2BbLNtSGA%3D";

    @Test
    void rawSignShouldProduceExpectedBase64() {
        assertEquals(RAW_SIGN, RobotSigningRules.sign(SECRET, TIMESTAMP));
    }

    @Test
    void bothPlatformSignsShouldBeUrlEncodedForQueryParameter() {
        assertEquals(ENCODED_SIGN, RobotSigningRules.signWecom(SECRET, TIMESTAMP));
        assertEquals(ENCODED_SIGN, RobotSigningRules.signDingtalk(SECRET, TIMESTAMP));
    }

    @Test
    void wecomUrlShouldContainEncodedSignOnly() {
        String url = RobotSigningRules.buildWecomUrl(
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo", SECRET, TIMESTAMP);

        assertEquals("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=demo"
                + "&timestamp=" + TIMESTAMP + "&sign=" + ENCODED_SIGN, url);
        assertTrue(url.contains("%2B"));
        assertTrue(url.contains("%2F"));
        assertTrue(url.contains("%3D"));
        assertFalse(url.contains("&sign=" + RAW_SIGN));
    }

    @Test
    void wecomUrlShouldUseQuestionMarkWhenBaseHasNoQuery() {
        String url = RobotSigningRules.buildWecomUrl("https://example.com/hook", SECRET, TIMESTAMP);
        assertTrue(url.startsWith("https://example.com/hook?timestamp=" + TIMESTAMP + "&sign=" + ENCODED_SIGN));
    }

    @Test
    void blankSecretShouldBeRejected() {
        assertThrows(IllegalArgumentException.class, () -> RobotSigningRules.sign("  ", TIMESTAMP));
        assertThrows(IllegalArgumentException.class, () -> RobotSigningRules.signWecom(null, TIMESTAMP));
    }
}
