package com.acme.scaffold.common.idempotency.rules;

import com.acme.scaffold.common.idempotency.IdempotencyStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IdempotencyRecordRules} 纯函数规则单测：筛选值归一（容错 posture）、
 * 时间窗收敛、过期判定与截断。无 Spring / DB 依赖，离线可执行。
 */
class IdempotencyRecordRulesTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 12, 0, 0);

    // ---------- normalizeStatus ----------

    @Test
    @DisplayName("IR-01 状态归一：三态大小写不敏感，trim 后命中")
    void normalizeStatus_acceptsAllStatesCaseInsensitive() {
        assertEquals(IdempotencyStatus.PROCESSING.name(),
                IdempotencyRecordRules.normalizeStatus(" processing "));
        assertEquals(IdempotencyStatus.SUCCESS.name(),
                IdempotencyRecordRules.normalizeStatus("Success"));
        assertEquals(IdempotencyStatus.FAILED.name(),
                IdempotencyRecordRules.normalizeStatus("FAILED"));
    }

    @Test
    @DisplayName("IR-02 状态归一：未知值与空值返回 null（容错忽略而非报错）")
    void normalizeStatus_rejectsUnknown() {
        assertNull(IdempotencyRecordRules.normalizeStatus("UNKNOWN"));
        assertNull(IdempotencyRecordRules.normalizeStatus(""));
        assertNull(IdempotencyRecordRules.normalizeStatus("   "));
        assertNull(IdempotencyRecordRules.normalizeStatus(null));
    }

    // ---------- normalizeScope / normalizeUserId ----------

    @Test
    @DisplayName("IR-03 接口域归一：trim 后保留原值，空值忽略")
    void normalizeScope_trimsAndIgnoresBlank() {
        assertEquals("workflow:complete", IdempotencyRecordRules.normalizeScope("  workflow:complete  "));
        assertNull(IdempotencyRecordRules.normalizeScope("  "));
        assertNull(IdempotencyRecordRules.normalizeScope(null));
    }

    @Test
    @DisplayName("IR-04 接口域归一：超宽截断至列宽 128（不报错）")
    void normalizeScope_truncatesToColumnWidth() {
        String longScope = "s".repeat(200);
        assertEquals(128, IdempotencyRecordRules.normalizeScope(longScope).length());
    }

    @Test
    @DisplayName("IR-05 用户 ID 归一：数字串转 Long，非数字/负数/空忽略")
    void normalizeUserId_parsesOrIgnores() {
        assertEquals(42L, IdempotencyRecordRules.normalizeUserId("42"));
        assertEquals(42L, IdempotencyRecordRules.normalizeUserId(" 42 "));
        assertEquals(0L, IdempotencyRecordRules.normalizeUserId("0"));
        assertNull(IdempotencyRecordRules.normalizeUserId("abc"));
        assertNull(IdempotencyRecordRules.normalizeUserId("-1"));
        assertNull(IdempotencyRecordRules.normalizeUserId(""));
        assertNull(IdempotencyRecordRules.normalizeUserId(null));
    }

    // ---------- wrapKeyword ----------

    @Test
    @DisplayName("IR-06 关键字归一：trim 后包裹 % 通配，空值忽略")
    void wrapKeyword_wrapsOrIgnores() {
        assertEquals("%order-2026%", IdempotencyRecordRules.wrapKeyword("  order-2026  "));
        assertNull(IdempotencyRecordRules.wrapKeyword("   "));
        assertNull(IdempotencyRecordRules.wrapKeyword(null));
    }

    @Test
    @DisplayName("IR-07 关键字归一：超长截断至 64 字符（含通配符总长 66）")
    void wrapKeyword_truncates() {
        String pattern = IdempotencyRecordRules.wrapKeyword("k".repeat(200));
        assertEquals("%" + "k".repeat(64) + "%", pattern);
    }

    // ---------- clampWindow ----------

    @Test
    @DisplayName("IR-08 时间窗收敛：起止皆空时默认最近 7 天")
    void clampWindow_defaultsToLastSevenDays() {
        IdempotencyRecordRules.Window w = IdempotencyRecordRules.clampWindow(null, null, NOW);
        assertEquals(NOW.minusDays(7), w.begin());
        assertEquals(NOW, w.end());
    }

    @Test
    @DisplayName("IR-09 时间窗收敛：起晚于止时钳为同日（区间为空但不报错）")
    void clampWindow_clampsInvertedRange() {
        IdempotencyRecordRules.Window w = IdempotencyRecordRules.clampWindow(
                NOW, NOW.minusDays(3), NOW);
        assertEquals(NOW.minusDays(3), w.begin());
        assertEquals(NOW.minusDays(3), w.end());
    }

    @Test
    @DisplayName("IR-10 时间窗收敛：显式区间原样保留，止为空落到当前")
    void clampWindow_keepsExplicitRange() {
        LocalDateTime start = NOW.minusDays(2);
        IdempotencyRecordRules.Window w = IdempotencyRecordRules.clampWindow(start, null, NOW);
        assertEquals(start, w.begin());
        assertEquals(NOW, w.end());
    }

    // ---------- isExpired ----------

    @Test
    @DisplayName("IR-11 过期判定：早于 now 为已过期，晚于 now 或 null 为未过期")
    void isExpired_boundary() {
        assertTrue(IdempotencyRecordRules.isExpired(NOW.minusSeconds(1), NOW));
        assertFalse(IdempotencyRecordRules.isExpired(NOW.plusSeconds(1), NOW));
        assertFalse(IdempotencyRecordRules.isExpired(NOW, NOW));
        assertNullSafeIsExpired();
    }

    private void assertNullSafeIsExpired() {
        assertFalse(IdempotencyRecordRules.isExpired(null, NOW));
    }

    // ---------- truncate ----------

    @Test
    @DisplayName("IR-12 通用截断：null 原样、未超长原样、超长截取")
    void truncate_basics() {
        assertNull(IdempotencyRecordRules.truncate(null, 10));
        assertEquals("abc", IdempotencyRecordRules.truncate("abc", 10));
        assertEquals("abcd", IdempotencyRecordRules.truncate("abcdef", 4));
        assertEquals("", IdempotencyRecordRules.truncate("abc", 0));
    }
}
