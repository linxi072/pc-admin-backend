package com.acme.scaffold.common.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditRetentionRulesTest {

    @Test
    @DisplayName("cutoff：正留存天数返回 now 往前推 N 天")
    void cutoffPositiveDays() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime cutoff = AuditRetentionRules.cutoff(30);
        LocalDateTime after = LocalDateTime.now();
        assertTrue(cutoff.isAfter(before.minusDays(30).minusSeconds(2)));
        assertTrue(cutoff.isBefore(after.minusDays(30).plusSeconds(2)));
    }

    @Test
    @DisplayName("cutoff：留存天数 ≤0 视为禁用清理，返回 null")
    void cutoffDisabled() {
        assertNull(AuditRetentionRules.cutoff(0));
        assertNull(AuditRetentionRules.cutoff(-7));
    }

    @Test
    @DisplayName("isExpired：发生时间早于 cutoff 即过期；空值不误判")
    void isExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        assertTrue(AuditRetentionRules.isExpired(cutoff.minusDays(1), cutoff));
        assertFalse(AuditRetentionRules.isExpired(cutoff.plusDays(1), cutoff));
        assertFalse(AuditRetentionRules.isExpired(null, cutoff));
        assertFalse(AuditRetentionRules.isExpired(cutoff.minusDays(1), null));
    }
}
