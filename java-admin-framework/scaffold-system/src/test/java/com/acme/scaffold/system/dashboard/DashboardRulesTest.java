package com.acme.scaffold.system.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DashboardRules} 纯函数测试：非负钳制、时间戳回落、字段顺序、趋势补齐与天数钳制。
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

    @Test
    @DisplayName("clampDays 越界被钳制到 [1,90]")
    void clampDaysBounds() {
        assertEquals(1, DashboardRules.clampDays(0));
        assertEquals(1, DashboardRules.clampDays(-5));
        assertEquals(DashboardRules.MAX_TREND_DAYS, DashboardRules.clampDays(1000));
        assertEquals(30, DashboardRules.clampDays(30));
    }

    @Test
    @DisplayName("fillDailyGaps 补齐缺失日为 0 且连续有序")
    void fillDailyGapsFillsZeros() {
        LocalDate today = LocalDate.of(2026, 10, 26);
        List<TrendPoint> points = List.of(
                new TrendPoint(LocalDate.of(2026, 10, 24), 4),
                new TrendPoint(LocalDate.of(2026, 10, 26), 9));
        List<TrendPoint> filled = DashboardRules.fillDailyGaps(points, 3, today);
        assertEquals(3, filled.size());
        assertEquals(LocalDate.of(2026, 10, 24), filled.get(0).date());
        assertEquals(4, filled.get(0).value());
        assertEquals(LocalDate.of(2026, 10, 25), filled.get(1).date());
        assertEquals(0, filled.get(1).value());
        assertEquals(LocalDate.of(2026, 10, 26), filled.get(2).date());
        assertEquals(9, filled.get(2).value());
    }

    @Test
    @DisplayName("fillDailyGaps 负数值被钳制为 0")
    void fillDailyGapsClampsNegative() {
        LocalDate today = LocalDate.of(2026, 10, 26);
        List<TrendPoint> points = List.of(new TrendPoint(LocalDate.of(2026, 10, 26), -3));
        List<TrendPoint> filled = DashboardRules.fillDailyGaps(points, 1, today);
        assertEquals(0, filled.get(0).value());
    }

    @Test
    @DisplayName("buildTrend 天数越界钳制且 generatedAt 为 0 时回落")
    void buildTrendClampsAndFallback() {
        LocalDate today = LocalDate.of(2026, 10, 26);
        DashboardTrend t = DashboardRules.buildTrend(List.of(), List.of(), List.of(), 1000, today, 0L);
        assertEquals(DashboardRules.MAX_TREND_DAYS, t.days());
        assertEquals(DashboardRules.MAX_TREND_DAYS, t.newUsers().size());
        assertTrue(t.generatedAt() > 0);
    }
}
