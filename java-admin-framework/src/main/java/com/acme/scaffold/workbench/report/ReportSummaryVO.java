package com.acme.scaffold.workbench.report;

/**
 * 报表汇总指标。
 *
 * @param totalVisits   窗口内操作总量（访问量）
 * @param activeUsers   窗口内去重操作人数（活跃用户数）
 * @param successRate   成功率（百分比，保留 1 位小数；无数据记 0.0）
 * @param moduleCount   涉及的功能模块数（去重 module_code 计数）
 * @param avgDurationMs 平均耗时（毫秒，无数据记 0）
 */
public record ReportSummaryVO(long totalVisits, long activeUsers, double successRate,
                              long moduleCount, long avgDurationMs) {
}
