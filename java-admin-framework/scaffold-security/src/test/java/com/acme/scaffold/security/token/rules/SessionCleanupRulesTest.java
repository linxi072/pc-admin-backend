package com.acme.scaffold.security.token.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 会话空闲回收纯规则单测（TEST-COVERAGE-PLAN 第二批 / T1 剩余）。
 * 锁定「last_used_at 优先、issued_at 兜底、无基准保守不回收」契约。
 */
class SessionCleanupRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);
    private static final Duration TIMEOUT = Duration.ofMinutes(30);

    @Test
    void shouldUseLastUsedAtWhenPresent() {
        assertTrue(SessionCleanupRules.isIdle(
                NOW.minusHours(1), NOW.minusMinutes(5), NOW, TIMEOUT), "lastUsed 已超时，即使 issued 很新");
        assertFalse(SessionCleanupRules.isIdle(
                NOW.minusMinutes(5), NOW.minusHours(1), NOW, TIMEOUT), "lastUsed 未超时，即使 issued 很旧");
    }

    @Test
    void shouldFallbackToIssuedAtWhenLastUsedMissing() {
        assertTrue(SessionCleanupRules.isIdle(null, NOW.minusHours(1), NOW, TIMEOUT));
        assertFalse(SessionCleanupRules.isIdle(null, NOW.minusMinutes(5), NOW, TIMEOUT));
    }

    @Test
    void shouldNotRecycleWithoutAnyActivityBaseline() {
        assertFalse(SessionCleanupRules.isIdle(null, null, NOW, TIMEOUT),
                "无活动基准时保守不回收，避免误杀刚签发的会话");
    }

    @Test
    void shouldNotRecycleWhenTimeoutDisabled() {
        assertFalse(SessionCleanupRules.isIdle(NOW.minusDays(1), NOW.minusDays(1), NOW, null));
        assertFalse(SessionCleanupRules.isIdle(NOW.minusDays(1), NOW.minusDays(1), NOW, Duration.ZERO));
        assertFalse(SessionCleanupRules.isIdle(NOW.minusDays(1), NOW.minusDays(1), NOW, Duration.ofSeconds(-1)));
    }

    @Test
    void boundaryAtExactTimeoutShouldNotRecycle() {
        // activity.plus(timeout).isBefore(now) —— 恰好等于 now 不算超时
        assertFalse(SessionCleanupRules.isIdle(NOW.minus(TIMEOUT), null, NOW, TIMEOUT));
        assertTrue(SessionCleanupRules.isIdle(NOW.minus(TIMEOUT).minusSeconds(1), null, NOW, TIMEOUT));
    }
}
