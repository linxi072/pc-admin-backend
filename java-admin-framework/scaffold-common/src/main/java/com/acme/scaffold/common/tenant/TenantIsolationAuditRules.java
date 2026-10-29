package com.acme.scaffold.common.tenant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 存量隔离自检规则（M45 纯函数）：将逐表违规统计聚合为可读报告。
 *
 * <p>规则与 {@link TenantIsolationRules} 同范式：仅做无副作用的聚合，不触碰数据库、不触发隔离逻辑。
 * 未归属行判定口径与 M19 强制隔离一致：{@code tenant_id IS NULL OR tenant_id = 0}
 * （同时排除已逻辑删除行，由调用方在查询侧过滤）。</p>
 */
public final class TenantIsolationAuditRules {

    private TenantIsolationAuditRules() {
    }

    /**
     * 聚合逐表违规统计为自检报告。
     *
     * @param violations          各业务表违规统计（violatedRows >= 0 已统计；-1 检查失败）
     * @param totalBusinessTables 纳入检查的业务表总数（用于报告上下文）
     * @return 自检报告（含 safeToEnable 判定与面向运维的说明）
     */
    public static TenantIsolationAuditReport audit(List<TenantIsolationTableViolation> violations,
                                                  int totalBusinessTables) {
        List<TenantIsolationTableViolation> byTable = new ArrayList<>(violations);
        // 检查失败的表（-1）置顶，其余按未归属行数降序，便于运维优先处理
        byTable.sort(Comparator.comparingLong(
                (TenantIsolationTableViolation v) -> v.violatedRows() < 0 ? Long.MAX_VALUE : -v.violatedRows()));

        long totalViolatedRows = 0;
        int violatedTables = 0;
        boolean anyUnknown = false;
        for (TenantIsolationTableViolation v : violations) {
            if (v.violatedRows() < 0) {
                anyUnknown = true;
                violatedTables++;
            } else if (v.violatedRows() > 0) {
                totalViolatedRows += v.violatedRows();
                violatedTables++;
            }
        }

        boolean safeToEnable = !anyUnknown && totalViolatedRows == 0;
        String message = buildMessage(byTable, totalViolatedRows, safeToEnable);

        return new TenantIsolationAuditReport(
                totalBusinessTables, violatedTables, totalViolatedRows, safeToEnable, byTable, message);
    }

    private static String buildMessage(List<TenantIsolationTableViolation> byTable,
                                       long totalViolatedRows, boolean safeToEnable) {
        if (safeToEnable) {
            return "业务表 tenant_id 归属完整，可安全开启 enforce（共 " + byTable.size() + " 张业务表通过检查）";
        }
        int problemTables = 0;
        for (TenantIsolationTableViolation v : byTable) {
            if (v.violatedRows() != 0) {
                problemTables++;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("存在 ").append(problemTables).append(" 张表共 ").append(totalViolatedRows)
                .append(" 行未归属数据（tenant_id 为 NULL 或 0），开启 enforce 前需先回填 tenant_id");
        List<TenantIsolationTableViolation> top = topOffenders(byTable, 3);
        if (!top.isEmpty()) {
            sb.append("；问题最严重的表：");
            List<String> parts = new ArrayList<>();
            for (TenantIsolationTableViolation v : top) {
                parts.add(v.violatedRows() < 0
                        ? v.tableName() + "(检查失败)"
                        : v.tableName() + "(" + v.violatedRows() + ")");
            }
            sb.append(String.join("、", parts));
        }
        return sb.toString();
    }

    private static List<TenantIsolationTableViolation> topOffenders(List<TenantIsolationTableViolation> list, int k) {
        List<TenantIsolationTableViolation> offenders = new ArrayList<>();
        for (TenantIsolationTableViolation v : list) {
            if (v.violatedRows() != 0) {
                offenders.add(v);
            }
        }
        offenders.sort(Comparator.comparingLong(
                (TenantIsolationTableViolation v) -> v.violatedRows() < 0 ? Long.MAX_VALUE : -v.violatedRows()));
        return offenders.subList(0, Math.min(k, offenders.size()));
    }
}
