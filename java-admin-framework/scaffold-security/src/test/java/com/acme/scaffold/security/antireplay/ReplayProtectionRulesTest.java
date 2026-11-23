package com.acme.scaffold.security.antireplay;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 防重放时间戳校验纯规则单测（代码审查专项）：
 * 覆盖缺失/非法/偏差判定，并回归极端值的 long 溢出。
 */
class ReplayProtectionRulesTest {

    @Test
    void shouldClassifyTimestampVerdicts() {
        long now = 1_700_000_000_000L;
        assertEquals(ReplayProtectionRules.TimestampVerdict.MISSING,
                ReplayProtectionRules.verifyTimestamp(null, now, 5_000L));
        assertEquals(ReplayProtectionRules.TimestampVerdict.INVALID,
                ReplayProtectionRules.verifyTimestamp(0L, now, 5_000L));
        assertEquals(ReplayProtectionRules.TimestampVerdict.INVALID,
                ReplayProtectionRules.verifyTimestamp(-1L, now, 5_000L));
        assertEquals(ReplayProtectionRules.TimestampVerdict.OK,
                ReplayProtectionRules.verifyTimestamp(now + 1000, now, 5_000L));
        assertEquals(ReplayProtectionRules.TimestampVerdict.TOO_OLD,
                ReplayProtectionRules.verifyTimestamp(now - 10_000L, now, 5_000L));
        assertEquals(ReplayProtectionRules.TimestampVerdict.TOO_FUTURE,
                ReplayProtectionRules.verifyTimestamp(now + 10_000L, now, 5_000L));
    }

    @Test
    void skewBoundaryShouldBeInclusive() {
        long now = 1_000_000L;
        assertEquals(ReplayProtectionRules.TimestampVerdict.OK,
                ReplayProtectionRules.verifyTimestamp(now + 5_000L, now, 5_000L));
        assertEquals(ReplayProtectionRules.TimestampVerdict.TOO_FUTURE,
                ReplayProtectionRules.verifyTimestamp(now + 5_001L, now, 5_000L));
    }

    @Test
    void extremeValuesShouldNotOverflowVerdict() {
        // 回归缺陷：原 Math.abs(client - server) 在极端值下溢出为负，
        // 使 diff > maxSkew 判断失效而错误放行。
        assertEquals(ReplayProtectionRules.TimestampVerdict.TOO_FUTURE,
                ReplayProtectionRules.verifyTimestamp(Long.MAX_VALUE, 0L, 5_000L));
        assertEquals(ReplayProtectionRules.TimestampVerdict.TOO_FUTURE,
                ReplayProtectionRules.verifyTimestamp(Long.MAX_VALUE, -1L, 5_000L));
        // serverNow 为极大正、client 为正且远小于它：TOO_OLD
        assertEquals(ReplayProtectionRules.TimestampVerdict.TOO_OLD,
                ReplayProtectionRules.verifyTimestamp(1L, Long.MAX_VALUE, 5_000L));
    }
}
