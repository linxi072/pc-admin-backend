package com.acme.scaffold.workbench.report;

/**
 * 分布分桶单点（耗时分布 / 响应时间分布 / 登录类型分布等）。
 *
 * @param label 分桶标签（如 {@code "<100ms"} / {@code "100-500ms"} / {@code "密码登录"}）
 * @param count 该分桶内的记录数
 * @param ratio 占全部记录数的百分比（保留 1 位小数；总量为 0 时记 0.0）
 */
public record ReportDistributionVO(String label, long count, double ratio) {
}
