package com.acme.scaffold.system.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DashboardService} 聚合测试：以假 {@link SystemStatsPort} 验证聚合与归一逻辑
 * （不依赖数据库 / Spring 上下文）。
 */
class DashboardServiceTest {

    /** 假实现：固定返回预设计数，验证聚合与规则归一。 */
    static class FakeStatsPort implements SystemStatsPort {
        long tenants = 2, users = 100, roles = 10, menus = 50, online = 7,
                idem = 30, ops = 5000, loginToday = 42;
        List<TrendPoint> newUsers = List.of(
                new TrendPoint(LocalDate.of(2026, 10, 25), 3),
                new TrendPoint(LocalDate.of(2026, 10, 26), 5));
        List<TrendPoint> newTenants = List.of();
        List<TrendPoint> activeTenants = List.of(new TrendPoint(LocalDate.of(2026, 10, 26), 2));

        @Override public long countTenants() { return tenants; }
        @Override public long countUsers() { return users; }
        @Override public long countRoles() { return roles; }
        @Override public long countMenus() { return menus; }
        @Override public long countOnlineSessions() { return online; }
        @Override public long countIdempotencyRecords() { return idem; }
        @Override public long countOperationLogs() { return ops; }
        @Override public long countLoginLogsToday() { return loginToday; }
        @Override public List<TrendPoint> dailyNewUsers(int days) { return newUsers; }
        @Override public List<TrendPoint> dailyNewTenants(int days) { return newTenants; }
        @Override public List<TrendPoint> dailyActiveTenants(int days) { return activeTenants; }
    }

    @Test
    @DisplayName("overview 聚合各数据源并经规则归一")
    void overviewAggregates() {
        DashboardService svc = new DashboardService(new FakeStatsPort());

        DashboardOverview o = svc.overview();
        assertEquals(2, o.tenantCount());
        assertEquals(100, o.userCount());
        assertEquals(10, o.roleCount());
        assertEquals(50, o.menuCount());
        assertEquals(7, o.onlineSessionCount());
        assertEquals(30, o.idempotencyRecordCount());
        assertEquals(5000, o.operationLogCount());
        assertEquals(42, o.loginLogTodayCount());
        assertTrue(o.generatedAt() > 0);
    }

    @Test
    @DisplayName("负数输入被规则钳制为 0")
    void negativeClamped() {
        FakeStatsPort port = new FakeStatsPort();
        port.users = -10;
        DashboardService svc = new DashboardService(port);
        assertEquals(0, svc.overview().userCount());
    }

    @Test
    @DisplayName("全零数据源得到全零快照（仅时间戳填充）")
    void allZeroSource() {
        DashboardService svc = new DashboardService(new FakeStatsPort() {
            @Override public long countTenants() { return 0; }
            @Override public long countUsers() { return 0; }
            @Override public long countRoles() { return 0; }
            @Override public long countMenus() { return 0; }
            @Override public long countOnlineSessions() { return 0; }
            @Override public long countIdempotencyRecords() { return 0; }
            @Override public long countOperationLogs() { return 0; }
            @Override public long countLoginLogsToday() { return 0; }
        });
        DashboardOverview o = svc.overview();
        assertEquals(0, o.tenantCount());
        assertEquals(0, o.operationLogCount());
        assertTrue(o.generatedAt() > 0);
    }

    @Test
    @DisplayName("trend 返回三条连续序列且长度=钳制后天数")
    void trendReturnsContinuousSeries() {
        DashboardService svc = new DashboardService(new FakeStatsPort());
        DashboardTrend t = svc.trend(30);
        assertEquals(30, t.days());
        assertEquals(30, t.newUsers().size());
        assertEquals(30, t.newTenants().size());
        assertEquals(30, t.activeTenants().size());
        assertTrue(t.generatedAt() > 0);
        // 连续日期：相邻相差 1 天
        for (int i = 1; i < t.newUsers().size(); i++) {
            long gap = java.time.temporal.ChronoUnit.DAYS.between(
                    t.newUsers().get(i - 1).date(), t.newUsers().get(i).date());
            assertEquals(1, gap);
        }
    }

    @Test
    @DisplayName("trend 天数越界被钳制到 [1,90]")
    void trendDaysClamped() {
        DashboardService svc = new DashboardService(new FakeStatsPort());
        assertEquals(1, svc.trend(0).days());
        assertEquals(DashboardRules.MAX_TREND_DAYS, svc.trend(1000).days());
        assertEquals(7, svc.trend(7).days());
    }
}
