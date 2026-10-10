package com.acme.scaffold.security.token;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionCleanupRulesTest {

    private final LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);
    private final Duration idle = Duration.ofMinutes(30);

    @Test
    void notIdleWhenRecentlyUsed() {
        LocalDateTime lastUsed = now.minus(Duration.ofMinutes(10));
        assertFalse(SessionCleanupRules.isIdle(lastUsed, now.minus(Duration.ofHours(2)), now, idle));
    }

    @Test
    void idleWhenLastUsedOlderThanThreshold() {
        LocalDateTime lastUsed = now.minus(Duration.ofMinutes(40));
        assertTrue(SessionCleanupRules.isIdle(lastUsed, now.minus(Duration.ofHours(2)), now, idle));
    }

    @Test
    void fallsBackToIssuedAtWhenLastUsedNull() {
        // last_used_at 为 null，回退到 issued_at（2 小时前）→ 空闲
        assertTrue(SessionCleanupRules.isIdle(null, now.minus(Duration.ofHours(2)), now, idle));
        // issuedAt 仅 10 分钟前 → 仍活跃
        assertFalse(SessionCleanupRules.isIdle(null, now.minus(Duration.ofMinutes(10)), now, idle));
    }

    @Test
    void noActivityBaselineIsConservative() {
        // 既无 last_used_at 也无 issued_at：保守不回收
        assertFalse(SessionCleanupRules.isIdle(null, null, now, idle));
    }

    @Test
    void disabledWhenTimeoutNotPositive() {
        LocalDateTime lastUsed = now.minus(Duration.ofHours(5));
        assertFalse(SessionCleanupRules.isIdle(lastUsed, null, now, Duration.ZERO));
        assertFalse(SessionCleanupRules.isIdle(lastUsed, null, now, Duration.ofMinutes(-1)));
        assertFalse(SessionCleanupRules.isIdle(lastUsed, null, now, null));
    }

    @Test
    void boundaryExactlyAtThresholdIsNotIdle() {
        // 活动时间为 now - idle 恰好：plus(idle) == now，isBefore(now) 为 false → 不回收
        LocalDateTime lastUsed = now.minus(idle);
        assertFalse(SessionCleanupRules.isIdle(lastUsed, null, now, idle));
    }
}
