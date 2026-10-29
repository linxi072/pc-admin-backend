package com.acme.scaffold.common.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TenantIsolationAuditRulesTest {

    @Test
    @DisplayName("全部表无违规时 safeToEnable=true")
    void safeWhenAllClean() {
        TenantIsolationAuditReport report = TenantIsolationAuditRules.audit(List.of(
                new TenantIsolationTableViolation("sys_user", 0L),
                new TenantIsolationTableViolation("sys_role", 0L)), 2);
        assertTrue(report.safeToEnable());
        assertEquals(2, report.totalBusinessTables());
        assertEquals(0, report.violatedTables());
        assertEquals(0L, report.totalViolatedRows());
    }

    @Test
    @DisplayName("存在未归属行时 safeToEnable=false 且计入合计")
    void unsafeWhenViolations() {
        TenantIsolationAuditReport report = TenantIsolationAuditRules.audit(List.of(
                new TenantIsolationTableViolation("sys_user", 0L),
                new TenantIsolationTableViolation("sys_order", 42L)), 2);
        assertFalse(report.safeToEnable());
        assertEquals(1, report.violatedTables());
        assertEquals(42L, report.totalViolatedRows());
    }

    @Test
    @DisplayName("存在检查失败的表时 safeToEnable=false（不计入行数合计）")
    void unsafeWhenUnknown() {
        TenantIsolationAuditReport report = TenantIsolationAuditRules.audit(List.of(
                new TenantIsolationTableViolation("sys_user", -1L)), 1);
        assertFalse(report.safeToEnable());
        assertEquals(1, report.violatedTables());
        assertEquals(0L, report.totalViolatedRows());
    }

    @Test
    @DisplayName("空清单按表总数生成报告且安全")
    void emptyList() {
        TenantIsolationAuditReport report = TenantIsolationAuditRules.audit(List.of(), 0);
        assertTrue(report.safeToEnable());
        assertEquals(0, report.totalBusinessTables());
    }
}
