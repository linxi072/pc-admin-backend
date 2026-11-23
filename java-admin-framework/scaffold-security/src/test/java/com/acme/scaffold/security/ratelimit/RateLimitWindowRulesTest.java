package com.acme.scaffold.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 固定窗口对齐规则单测（缺陷修复回归）：
 * 覆盖窗口起点对齐、非正窗口归一（防御除零），以及观测与限流使用同一算法。
 */
class RateLimitWindowRulesTest {

    @Test
    void shouldAlignWindowStartToMultiple() {
        long now = 1_700_000_000_123L;
        long start = RateLimitWindowRules.windowStart(now, 60);
        assertEquals(0, start % 60_000L, "窗口起点对齐到窗口长度整数倍");
        assertTrue(start <= now && now - start < 60_000L);
    }

    @Test
    void differentWindowSizesShouldAlignIndependently() {
        long now = 123_456_789L;
        assertEquals(0, RateLimitWindowRules.windowStart(now, 10) % 10_000L);
        assertEquals(0, RateLimitWindowRules.windowStart(now, 60) % 60_000L);
    }

    @Test
    void nonPositiveWindowShouldNormalizeToOneSecond() {
        // 回归缺陷：windowSeconds<=0 时 %0 抛 ArithmeticException / 负数错误对齐。
        assertEquals(1_000L, RateLimitWindowRules.windowMillis(0));
        assertEquals(1_000L, RateLimitWindowRules.windowMillis(-5));
        for (int bad : new int[]{0, -1, -60}) {
            long start = RateLimitWindowRules.windowStart(12_345L, bad);
            assertEquals(0, start % 1_000L, "非正窗口归一为 1s 后正常对齐");
        }
    }

    @Test
    void defaultWindowShouldBe60Seconds() {
        assertEquals(60, RateLimitWindowRules.DEFAULT_WINDOW_SECONDS);
        assertEquals(60_000L, RateLimitWindowRules.windowMillis(RateLimitWindowRules.DEFAULT_WINDOW_SECONDS));
    }
}
