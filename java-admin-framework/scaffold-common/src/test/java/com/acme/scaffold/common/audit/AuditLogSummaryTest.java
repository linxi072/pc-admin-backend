package com.acme.scaffold.common.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditLogSummary} 纯函数单测：聚焦 {@code sensitiveVisible} 标识的契约行为。
 *
 * <p>该字段用于前端渲染「明文 / 已脱敏」徽标，必须由后端按权限决定后随摘要回传，
 * 前端不应自行判定权限。约定：{@code null} 视为不可见；{@code empty()} 默认不可见。</p>
 */
class AuditLogSummaryTest {

    @Test
    @DisplayName("sensitiveVisible 为 true 时原样保留")
    void preservesTrueSensitiveVisible() {
        AuditLogSummary s = new AuditLogSummary(10L, 8L, 2L, 1L, 120L, 300L, Boolean.TRUE);
        assertTrue(s.sensitiveVisible(), "高权限主体的摘要应标记为可见，前端据此渲染明文");
        assertEquals(10L, s.total());
        assertEquals(80.0, s.successRate());
    }

    @Test
    @DisplayName("sensitiveVisible 为 false 时原样保留")
    void preservesFalseSensitiveVisible() {
        AuditLogSummary s = new AuditLogSummary(5L, 5L, 0L, 0L, 12L, 20L, Boolean.FALSE);
        assertFalse(s.sensitiveVisible(), "无权限主体的摘要应标记为不可见，前端据此渲染脱敏徽标");
    }

    @Test
    @DisplayName("sensitiveVisible 为 null 时收口为 false，避免前端空指针歧义")
    void nullSensitiveVisibleCoercedToFalse() {
        AuditLogSummary s = new AuditLogSummary(0L, 0L, 0L, 0L, 0L, 0L, null);
        assertFalse(s.sensitiveVisible(), "null 应等价于不可见，前端始终有确定状态");
    }

    @Test
    @DisplayName("empty() 默认 sensitiveVisible=false，与空区间统计口径一致")
    void emptyDefaultsToNotVisible() {
        AuditLogSummary s = AuditLogSummary.empty();
        assertEquals(0L, s.total());
        assertFalse(s.sensitiveVisible(), "空摘要也需明确标识不可见，前端徽标状态与列表保持一致");
    }

    @Test
    @DisplayName("耗时负值被钳为 0，避免脏数据外泄")
    void clampsNegativeDurations() {
        AuditLogSummary s = new AuditLogSummary(1L, 1L, 0L, 0L, -5L, -10L, Boolean.TRUE);
        assertEquals(0L, s.avgDurationMs());
        assertEquals(0L, s.maxDurationMs());
    }
}
