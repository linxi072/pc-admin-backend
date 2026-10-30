package com.acme.scaffold.common.tenant;

/**
 * 单张业务表的 tenant_id 归属违规统计（M45 存量隔离自检）。
 *
 * <p>{@code violatedRows >= 0} 表示已成功统计的未归属行数（tenant_id 为 NULL 或 0、且未逻辑删除）；
 * {@code violatedRows == -1} 表示该表检查失败（如列缺失或查询异常），需人工复核，不能视为"安全"。</p>
 *
 * @param tableName     业务表名
 * @param violatedRows 未归属行数；-1 表示检查失败/未知
 */
public record TenantIsolationTableViolation(String tableName, long violatedRows) {
}
