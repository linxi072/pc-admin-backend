package com.acme.scaffold.common.audit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 审计留存纯函数规则单测（E6）：留存窗口禁用、过期判定与 null 安全。 */
class AuditRetentionRulesTest {

    @Test
    void shouldDisableCleanupWhenRetentionNonPositive() {
        assertNull(AuditRetentionRules.cutoff(0));
        assertNull(AuditRetentionRules.cutoff(-1));
    }

    @Test
    void shouldComputeCutoffDaysAgo() {
        LocalDateTime before = LocalDateTime.now().minusDays(7).minusSeconds(2);
        LocalDateTime after = LocalDateTime.now().minusDays(7).plusSeconds(2);
        LocalDateTime cutoff = AuditRetentionRules.cutoff(7);
        assertTrue(cutoff.isAfter(before));
        assertTrue(cutoff.isBefore(after));
    }

    @Test
    void shouldJudgeExpiryWithNullSafety() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertTrue(AuditRetentionRules.isExpired(LocalDateTime.of(2025, 12, 31, 23, 59), cutoff));
        assertFalse(AuditRetentionRules.isExpired(cutoff, cutoff));
        assertFalse(AuditRetentionRules.isExpired(LocalDateTime.of(2026, 1, 2, 0, 0), cutoff));
        assertFalse(AuditRetentionRules.isExpired(null, cutoff));
        assertFalse(AuditRetentionRules.isExpired(LocalDateTime.now(), null));
    }
}
