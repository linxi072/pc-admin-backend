package com.acme.scaffold.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 租户隔离存量自检聚合单测（E6）：空列表、全通过、违规统计、检查失败置顶与文案。 */
class TenantIsolationAuditRulesTest {

    @Test
    void shouldHandleEmptyInput() {
        var report = TenantIsolationAuditRules.audit(List.of(), 0);
        assertTrue(report.safeToEnable());
        assertEquals(0, report.violatedTables());
        assertEquals(0, report.totalViolatedRows());
        assertTrue(report.message().contains("可安全开启"));
    }

    @Test
    void shouldMarkSafeWhenNoViolations() {
        var report = TenantIsolationAuditRules.audit(
                List.of(new TenantIsolationTableViolation("a", 0), new TenantIsolationTableViolation("b", 0)), 2);
        assertTrue(report.safeToEnable());
        assertEquals(2, report.totalBusinessTables());
        assertEquals(2, report.byTable().size());
    }

    @Test
    void shouldAggregateViolationsAndSortDescending() {
        var report = TenantIsolationAuditRules.audit(List.of(
                new TenantIsolationTableViolation("small", 1),
                new TenantIsolationTableViolation("large", 50)), 5);
        assertFalse(report.safeToEnable());
        assertEquals(51, report.totalViolatedRows());
        assertEquals(2, report.violatedTables());
        assertEquals("large", report.byTable().get(0).tableName());
        assertTrue(report.message().contains("2 张表共 51 行"));
        assertTrue(report.message().contains("large(50)"));
    }

    @Test
    void shouldPutFailedCheckFirstAndNeverSafe() {
        var report = TenantIsolationAuditRules.audit(List.of(
                new TenantIsolationTableViolation("huge", 999),
                new TenantIsolationTableViolation("failed", -1)), 2);
        assertEquals("failed", report.byTable().get(0).tableName());
        assertFalse(report.safeToEnable());
        assertEquals(999, report.totalViolatedRows()); // 检查失败不计入合计
        assertTrue(report.message().contains("failed(检查失败)"));
    }
}
