package com.acme.scaffold.common.tenant;

import java.util.List;

/**
 * 存量隔离自检报告（M45 只读运维诊断）：扫描全部业务表 tenant_id 归属情况，
 * 给出"可否安全开启 enforce"的判定，供运维在开启多租户强制隔离前预检。
 *
 * @param totalBusinessTables 纳入检查的业务表总数
 * @param violatedTables      存在未归属数据的表数量（含检查失败的表）
 * @param totalViolatedRows   未归属行数合计（检查失败的表不计入合计，但会使 safeToEnable=false）
 * @param safeToEnable       是否可安全开启 enforce（全部业务表均无未归属数据且无非检查失败）
 * @param byTable            各表违规明细（按未归属行数/检查失败降序）
 * @param message            面向运维的判定说明（含最严重的问题表）
 */
public record TenantIsolationAuditReport(
        int totalBusinessTables,
        int violatedTables,
        long totalViolatedRows,
        boolean safeToEnable,
        List<TenantIsolationTableViolation> byTable,
        String message) {
}
