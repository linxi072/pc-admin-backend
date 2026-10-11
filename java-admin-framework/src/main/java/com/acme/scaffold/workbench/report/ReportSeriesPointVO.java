package com.acme.scaffold.workbench.report;

/**
 * 报表趋势单点（按时间桶聚合）。
 *
 * @param bucket      时间桶标签：小时粒度形如 {@code "08:00"}，天粒度形如 {@code "10-12"}
 * @param visits      该桶内的操作总量（访问量）
 * @param activeUsers 该桶内去重的操作人数量（活跃用户数）
 */
public record ReportSeriesPointVO(String bucket, long visits, long activeUsers) {
}
