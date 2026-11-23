package com.acme.scaffold.common.idempotency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.idempotency.rules.IdempotencyRecordRules;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * 幂等记录查询规则单测（TEST-COVERAGE-PLAN 第二批 / T2）。
 * 锁定「容错查询 posture」：非法筛选值忽略而非报错，时间窗收敛与过期判定。
 */
class IdempotencyRecordRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0);

    @Test
    void normalizeStatusShouldAcceptEnumNamesCaseInsensitive() {
        assertEquals("SUCCESS", IdempotencyRecordRules.normalizeStatus(" success "));
        assertEquals("PROCESSING", IdempotencyRecordRules.normalizeStatus("Processing"));
        assertNull(IdempotencyRecordRules.normalizeStatus(null));
        assertNull(IdempotencyRecordRules.normalizeStatus("  "));
        assertNull(IdempotencyRecordRules.normalizeStatus("UNKNOWN"), "非法值忽略");
    }

    @Test
    void normalizeScopeShouldTrimAndTruncate() {
        assertNull(IdempotencyRecordRules.normalizeScope(null));
        assertNull(IdempotencyRecordRules.normalizeScope("   "));
        String longScope = "a".repeat(200);
        assertEquals(IdempotencyRecordRules.MAX_SCOPE_LEN,
                IdempotencyRecordRules.normalizeScope(longScope).length());
        assertEquals("api:order", IdempotencyRecordRules.normalizeScope("  api:order  "));
    }

    @Test
    void normalizeUserIdShouldIgnoreInvalidAndNegative() {
        assertEquals(123L, IdempotencyRecordRules.normalizeUserId(" 123 "));
        assertNull(IdempotencyRecordRules.normalizeUserId(null));
        assertNull(IdempotencyRecordRules.normalizeUserId("abc"));
        assertNull(IdempotencyRecordRules.normalizeUserId("-1"), "负数忽略");
        assertEquals(0L, IdempotencyRecordRules.normalizeUserId("0"));
    }

    @Test
    void wrapKeywordShouldBuildLikePatternAndTruncate() {
        assertNull(IdempotencyRecordRules.wrapKeyword(null));
        assertNull(IdempotencyRecordRules.wrapKeyword("  "));
        assertEquals("%abc%", IdempotencyRecordRules.wrapKeyword(" abc "));
        String wrapped = IdempotencyRecordRules.wrapKeyword("x".repeat(100));
        assertEquals(IdempotencyRecordRules.MAX_KEYWORD_LEN + 2, wrapped.length());
    }

    @Test
    void clampWindowShouldApplyDefaultsAndClampInvertedRange() {
        IdempotencyRecordRules.Window defaults = IdempotencyRecordRules.clampWindow(null, null, NOW);
        assertEquals(NOW, defaults.end());
        assertEquals(NOW.minusDays(IdempotencyRecordRules.DEFAULT_WINDOW_DAYS), defaults.begin());

        IdempotencyRecordRules.Window withStart = IdempotencyRecordRules.clampWindow(
                NOW.minusDays(3), null, NOW);
        assertEquals(NOW.minusDays(3), withStart.begin());
        assertEquals(NOW, withStart.end());

        IdempotencyRecordRules.Window inverted = IdempotencyRecordRules.clampWindow(
                NOW.plusDays(1), NOW, NOW);
        assertEquals(NOW, inverted.begin(), "start 晚于 end 时钳为 end");
        assertEquals(NOW, inverted.end());
    }

    @Test
    void isExpiredShouldTreatNullAsNotExpired() {
        assertFalse(IdempotencyRecordRules.isExpired(null, NOW), "null 不过期（防御脏数据）");
        assertTrue(IdempotencyRecordRules.isExpired(NOW.minusSeconds(1), NOW));
        assertFalse(IdempotencyRecordRules.isExpired(NOW.plusSeconds(1), NOW));
    }
}
