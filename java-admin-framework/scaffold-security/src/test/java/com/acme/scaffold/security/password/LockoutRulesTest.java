package com.acme.scaffold.security.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 渐进锁定时长规则单测（代码审查专项）：
 * 锁定基础分级语义，并回归极大 maxDuration / 失败次数下的溢出与空转。
 */
class LockoutRulesTest {

    @Test
    void belowThresholdShouldNotLock() {
        assertFalse(LockoutRules.shouldLock(4, 5));
        assertEquals(Duration.ZERO,
                LockoutRules.computeDuration(4, 5, Duration.ofMinutes(15), Duration.ofHours(24)));
    }

    @Test
    void durationShouldGrowInTiersAndCap() {
        Duration base = Duration.ofMinutes(15);
        Duration max = Duration.ofHours(24);
        assertEquals(base, LockoutRules.computeDuration(5, 5, base, max));
        assertEquals(base.multipliedBy(2), LockoutRules.computeDuration(10, 5, base, max));
        assertEquals(max, LockoutRules.computeDuration(1_000, 5, base, max));
    }

    @Test
    void extremeMaxDurationShouldNotOverflow() {
        // 回归缺陷：maxDuration 极大时原实现 seconds*2 可能溢出为负，
        // 且极大失败次数下循环空转。修复后恒等于夹紧后的上界且非负。
        Duration huge = Duration.ofSeconds(Long.MAX_VALUE / 2);
        Duration d = LockoutRules.computeDuration(Integer.MAX_VALUE, 1, Duration.ofSeconds(1000), huge);
        assertTrue(!d.isNegative(), "锁定时长不得为负");
        assertEquals(Long.MAX_VALUE / 2, d.getSeconds());
    }

    @Test
    void baseBeyondMaxShouldBeClamped() {
        Duration max = Duration.ofMinutes(30);
        assertEquals(max, LockoutRules.computeDuration(5, 5, Duration.ofHours(2), max));
    }

    @Test
    void temporaryLockShouldExpireInsteadOfBecomingPermanent() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 26, 10, 0);
        assertTrue(LockoutRules.isLocked("LOCKED", now.plusMinutes(15), now));
        assertFalse(LockoutRules.isLocked("LOCKED", now.minusMinutes(1), now));
        assertFalse(LockoutRules.isLocked("LOCKED", now, now));
    }

    @Test
    void manualLockWithoutExpiryShouldRemainLocked() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 26, 10, 0);
        assertTrue(LockoutRules.isLocked("LOCKED", null, now));
    }

    @Test
    void futureExpiryShouldLockEvenWhenStatusIsActive() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 26, 10, 0);
        assertTrue(LockoutRules.isLocked("ACTIVE", now.plusSeconds(1), now));
        assertFalse(LockoutRules.isLocked("ACTIVE", null, now));
        assertFalse(LockoutRules.isLocked("DISABLED", now.minusSeconds(1), now));
    }
}
