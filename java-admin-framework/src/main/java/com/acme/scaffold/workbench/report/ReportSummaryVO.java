package com.acme.scaffold.workbench.report;

/**
 * 报表汇总指标。
 *
 * <p>字段在不同报表类型下口径不同（见各字段注释）；使用视角（USAGE/CATEGORY/STATUS）下
 * {@code failedVisits/maxDurationMs/p95DurationMs} 由审计日志实际计算填入。</p>
 *
 * @param totalVisits   窗口内主指标总量（访问量 / 执行次数 / 登录次数 / 接口调用量）
 * @param activeUsers   窗口内次指标总量（活跃用户数 / 涉及任务数 / 活跃登录用户 / 涉及端点数）
 * @param successRate   成功率（百分比，保留 1 位小数；无数据记 0.0）
 * @param moduleCount   涉及的功能模块数（去重 module_code 计数）
 * @param avgDurationMs 平均耗时（毫秒，无数据记 0）
 * @param failedVisits  窗口内失败次数（执行失败 / 登录失败 / 接口错误）
 * @param maxDurationMs 窗口内最大耗时（毫秒，无数据记 0）
 * @param p95DurationMs 窗口内 P95 耗时（毫秒，样本不足时退化为最大值，无数据记 0）
 */
public record ReportSummaryVO(long totalVisits, long activeUsers, double successRate,
                              long moduleCount, long avgDurationMs,
                              long failedVisits, long maxDurationMs, long p95DurationMs) {
}
