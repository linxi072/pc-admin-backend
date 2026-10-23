package com.acme.scaffold.system.notification.unsubscribe.rules;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.system.notification.unsubscribe.rules.UnsubscribeTokenRules.UnsubscribeTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UnsubscribeTokenRules} 测试：铸造/验签往返、篡改检测、时效、形态非法全拒。
 *
 * <p>纯函数离线单测，时钟由 {@code nowMillis} 入参注入，不依赖系统时间。
 */
@DisplayName("退订令牌：HMAC 签名铸造与验签")
class UnsubscribeTokenRulesTest {

    private static final String SECRET = "unit-test-secret";
    private static final long NOW = 1_700_000_000_000L;
    private static final long EXP = NOW + 72 * 3600 * 1000L;

    private static String mint(long userId, Channel channel, String msgType, long exp) {
        return UnsubscribeTokenRules.mint(SECRET, userId, channel, msgType, exp);
    }

    @Test
    @DisplayName("铸造后验签往返：五段 payload 完整还原")
    void mintThenVerify_roundTrips() {
        String token = mint(42L, Channel.EMAIL, "NOTICE", EXP);

        Optional<UnsubscribeTarget> parsed = UnsubscribeTokenRules.verify(SECRET, token, NOW);
        assertTrue(parsed.isPresent());
        UnsubscribeTarget t = parsed.get();
        assertEquals(42L, t.userId());
        assertEquals(Channel.EMAIL, t.channel());
        assertEquals("NOTICE", t.msgType());
        assertEquals(EXP, t.expMillis());
    }

    @Test
    @DisplayName("同一用户两次铸造互不相等（nonce 保证链接不可关联）")
    void mintIsNonDeterministic() {
        assertNotEquals(mint(42L, Channel.SMS, "ALERT", EXP),
                mint(42L, Channel.SMS, "ALERT", EXP));
    }

    @Test
    @DisplayName("含点号/连字符的消息类型往返不被分隔符截断")
    void msgTypeWithSpecialChars_roundTrips() {
        // payload 经 base64url 编码，点号等字符不与令牌分隔符冲突
        String token = mint(7L, Channel.EMAIL, "ORDER.SHIPPED-V2", EXP);

        Optional<UnsubscribeTarget> parsed = UnsubscribeTokenRules.verify(SECRET, token, NOW);
        assertTrue(parsed.isPresent());
        assertEquals("ORDER.SHIPPED-V2", parsed.get().msgType());
    }

    @Test
    @DisplayName("错误密钥验签失败")
    void wrongSecret_rejected() {
        String token = mint(42L, Channel.EMAIL, "NOTICE", EXP);
        assertTrue(UnsubscribeTokenRules.verify("other-secret", token, NOW).isEmpty());
    }

    @Test
    @DisplayName("篡改 payload 段验签失败（签名覆盖前两段）")
    void tamperedPayload_rejected() {
        String token = mint(42L, Channel.EMAIL, "NOTICE", EXP);
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "xx"
                + "." + parts[2];
        assertTrue(UnsubscribeTokenRules.verify(SECRET, forged, NOW).isEmpty());
    }

    @Test
    @DisplayName("篡改签名字段验签失败")
    void tamperedSignature_rejected() {
        String token = mint(42L, Channel.EMAIL, "NOTICE", EXP);
        String[] parts = token.split("\\.");
        String flipped = parts[2].startsWith("A") ? "B" + parts[2].substring(1)
                : "A" + parts[2].substring(1);
        assertTrue(UnsubscribeTokenRules.verify(SECRET, parts[0] + "." + parts[1] + "." + flipped, NOW)
                .isEmpty());
    }

    @Test
    @DisplayName("过期令牌被拒（exp <= now 即失效，边界含等号）")
    void expiredToken_rejected() {
        String token = mint(42L, Channel.EMAIL, "NOTICE", EXP);
        assertTrue(UnsubscribeTokenRules.verify(SECRET, token, EXP).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, token, EXP + 1).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, token, EXP - 1).isPresent());
    }

    @Test
    @DisplayName("形态非法全拒：段数/版本/base64/数字/枚举")
    void malformedTokens_rejected() {
        assertTrue(UnsubscribeTokenRules.verify(SECRET, null, NOW).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "", NOW).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "garbage", NOW).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "v1.only-one-part", NOW).isEmpty());
        // 版本段不符
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "v2.aaaa.bbbb", NOW).isEmpty());
        // payload base64 解码失败
        assertTrue(UnsubscribeTokenRules.verify(SECRET, "v1.!!!.sig", NOW).isEmpty());
    }

    @Test
    @DisplayName("密钥空白时任何令牌都不可用（功能未配置的安全失败）")
    void blankSecret_rejectsEverything() {
        String token = mint(42L, Channel.EMAIL, "NOTICE", EXP);
        assertTrue(UnsubscribeTokenRules.verify(null, token, NOW).isEmpty());
        assertTrue(UnsubscribeTokenRules.verify("  ", token, NOW).isEmpty());
    }

    @Test
    @DisplayName("密钥含非 ASCII 字符时往返一致（UTF-8 密钥规格）")
    void nonAsciiSecret_roundTrips() {
        String secret = "退订密钥-🔑";
        String token = UnsubscribeTokenRules.mint(secret, 9L, Channel.SMS, "CC", EXP);
        assertTrue(UnsubscribeTokenRules.verify(secret, token, NOW).isPresent());
    }
}
