package com.acme.scaffold.system.notification.rules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link RobotSigningRules} 测试：对齐企业微信 / 钉钉真实 HMAC-SHA256 签名规范。
 *
 * <p>参考向量由独立 Python 实现（hmac + hashlib）核对，确保断言的是真实算法而非自洽：
 * secret={@code "sec123"}、timestamp={@code 1700000000000} 时
 * 企微原始 base64 = {@code amfC83c79uXxUW+yxwHnANQg5VhpHhnDvILAvl4GQvg=}，
 * 钉钉 URL 编码 = {@code amfC83c79uXxUW%2ByxwHnANQg5VhpHhnDvILAvl4GQvg%3D}。</p>
 */
class RobotSigningRulesTest {

    private static final String SECRET = "sec123";
    private static final long TS = 1700000000000L;

    @Test
    void signWecomMatchesReferenceVector() {
        assertEquals("amfC83c79uXxUW+yxwHnANQg5VhpHhnDvILAvl4GQvg=",
                RobotSigningRules.signWecom(SECRET, TS));
    }

    @Test
    void signDingtalkMatchesReferenceVector() {
        assertEquals("amfC83c79uXxUW%2ByxwHnANQg5VhpHhnDvILAvl4GQvg%3D",
                RobotSigningRules.signDingtalk(SECRET, TS));
    }

    @Test
    void dingtalkSignIsUrlEncodedWecomSign() {
        String wecom = RobotSigningRules.signWecom(SECRET, TS);
        String dingtalk = RobotSigningRules.signDingtalk(SECRET, TS);
        assertEquals(dingtalk, java.net.URLEncoder.encode(wecom, java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void buildWecomUrlAppendsTimestampAndRawSign() {
        String url = RobotSigningRules.buildWecomUrl(
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=K", SECRET, TS);
        assertEquals(
                "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=K&timestamp=1700000000000"
                        + "&sign=amfC83c79uXxUW+yxwHnANQg5VhpHhnDvILAvl4GQvg=",
                url);
    }

    @Test
    void buildDingtalkUrlAppendsUrlEncodedSign() {
        String url = RobotSigningRules.buildDingtalkUrl(
                "https://oapi.dingtalk.com/robot/send?access_token=T", SECRET, TS);
        assertEquals(
                "https://oapi.dingtalk.com/robot/send?access_token=T&timestamp=1700000000000"
                        + "&sign=amfC83c79uXxUW%2ByxwHnANQg5VhpHhnDvILAvl4GQvg%3D",
                url);
    }

    @Test
    void buildWecomUrlWithoutExistingQueryUsesQuestionMark() {
        String url = RobotSigningRules.buildWecomUrl("https://example.com/hook", SECRET, TS);
        assertEquals("https://example.com/hook?timestamp=1700000000000"
                + "&sign=amfC83c79uXxUW+yxwHnANQg5VhpHhnDvILAvl4GQvg=", url);
    }

    @Test
    void signRejectsBlankSecret() {
        assertThrows(IllegalArgumentException.class, () -> RobotSigningRules.sign("  ", TS));
        assertThrows(IllegalArgumentException.class, () -> RobotSigningRules.sign(null, TS));
    }
}
