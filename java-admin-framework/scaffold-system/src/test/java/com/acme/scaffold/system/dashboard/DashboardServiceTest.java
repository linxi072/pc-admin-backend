package com.acme.scaffold.system.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.acme.scaffold.common.observability.ObservabilitySnapshot;
import com.acme.scaffold.common.observability.ObservabilityStatsPort;

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

    /** 假可观测端口：固定返回预置快照。 */
    static class FakeObservabilityPort implements ObservabilityStatsPort {
        ObservabilitySnapshot snapshot = new ObservabilitySnapshot(
                9, 4, 1_700_000_000_000L, true, 12, 45, 4);
        boolean thrown = false;

        @Override
        public ObservabilitySnapshot snapshot() {
            if (thrown) {
                throw new IllegalStateException("observability source unavailable");
            }
            return snapshot;
        }
    }

    @Test
    @DisplayName("overview 在有可观测端口时填充 N+1 与链路追踪指标字段")
    void overviewIncludesObservabilityWhenPortPresent() {
        DashboardService svc = new DashboardService(new FakeStatsPort());
        FakeObservabilityPort port = new FakeObservabilityPort();
        svc.setObservabilityPort(port);

        DashboardOverview o = svc.overview();
        assertEquals(9, o.nPlusOneSuspectTotal());
        assertEquals(4, o.nPlusOneSuspectRetained());
        assertEquals(1_700_000_000_000L, o.lastBreachAtMillis());
        assertTrue(o.traceRecorderEnabled());
        assertEquals(12, o.traceRetained());
        assertEquals(45, o.traceTotal());
        assertEquals(4, o.traceErrorTotal());
        // 系统域计数不受影响
        assertEquals(100, o.userCount());
    }

    @Test
    @DisplayName("未装配可观测端口时回落全零（EMPTY 语义）")
    void overviewFallsBackToEmptyWhenNoPort() {
        DashboardService svc = new DashboardService(new FakeStatsPort());
        // 不调用 setObservabilityPort

        DashboardOverview o = svc.overview();
        assertEquals(0, o.nPlusOneSuspectTotal());
        assertEquals(0, o.nPlusOneSuspectRetained());
        assertEquals(0, o.lastBreachAtMillis());
        assertEquals(false, o.traceRecorderEnabled());
        assertEquals(0, o.traceRetained());
        assertEquals(0, o.traceTotal());
        assertEquals(0, o.traceErrorTotal());
    }

    @Test
    @DisplayName("可观测端口抛异常时回落全零（看板不被阻断）")
    void overviewFallsBackToEmptyWhenPortThrows() {
        DashboardService svc = new DashboardService(new FakeStatsPort());
        FakeObservabilityPort port = new FakeObservabilityPort();
        port.thrown = true;
        svc.setObservabilityPort(port);

        DashboardOverview o = svc.overview();
        assertEquals(0, o.nPlusOneSuspectTotal());
        assertEquals(0, o.nPlusOneSuspectRetained());
        assertEquals(0, o.lastBreachAtMillis());
        assertEquals(false, o.traceRecorderEnabled());
        assertEquals(0, o.traceRetained());
        assertEquals(0, o.traceTotal());
        assertEquals(0, o.traceErrorTotal());
        // 系统域计数仍正常
        assertEquals(5000, o.operationLogCount());
    }
}
