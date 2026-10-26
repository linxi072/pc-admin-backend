package com.acme.scaffold.system.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DashboardRules} 纯函数测试：非负钳制、时间戳回落、字段顺序稳定。
 */
class DashboardRulesTest {

    @Test
    @DisplayName("负数计数被钳制为 0")
    void negativeClampedToZero() {
        DashboardOverview o = DashboardRules.buildOverview(-5, -1, 0, 0, 0, 0, 0, 0, 0);
        assertEquals(0, o.tenantCount());
        assertEquals(0, o.userCount());
    }

    @Test
    @DisplayName("generatedAt 为 0 时回落当前时刻")
    void generatedAtFallsBackToNow() {
        DashboardOverview o = DashboardRules.buildOverview(1, 2, 3, 4, 5, 6, 7, 8, 0);
        assertTrue(o.generatedAt() > 0);
    }

    @Test
    @DisplayName("正常计数原样保留")
    void normalPreserved() {
        DashboardOverview o = DashboardRules.buildOverview(1, 2, 3, 4, 5, 6, 7, 8, 123L);
        assertEquals(1, o.tenantCount());
        assertEquals(8, o.loginLogTodayCount());
        assertEquals(123L, o.generatedAt());
    }

    @Test
    @DisplayName("默认快照全零且带时间戳")
    void defaultOverview() {
        DashboardOverview o = DashboardRules.defaultOverview();
        assertEquals(0, o.tenantCount());
        assertTrue(o.generatedAt() > 0);
    }

    @Test
    @DisplayName("metricOrder 字段数量与快照字段一致（8 项）")
    void metricOrderMatches() {
        assertEquals(8, DashboardRules.metricOrder().size());
    }
}
