package com.acme.scaffold.system.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

        @Override public long countTenants() { return tenants; }
        @Override public long countUsers() { return users; }
        @Override public long countRoles() { return roles; }
        @Override public long countMenus() { return menus; }
        @Override public long countOnlineSessions() { return online; }
        @Override public long countIdempotencyRecords() { return idem; }
        @Override public long countOperationLogs() { return ops; }
        @Override public long countLoginLogsToday() { return loginToday; }
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
}
