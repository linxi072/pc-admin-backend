package com.acme.scaffold.workbench.report.dto;

/**
 * 报表趋势单点（按时间桶聚合）。
 *
 * @param bucket      时间桶标签：小时粒度形如 {@code "08:00"}，天粒度形如 {@code "10-12"}
 * @param visits      该桶内的主指标量（使用视角=访问量；任务视角=执行次数；登录视角=登录次数；接口视角=调用量）
 * @param activeUsers 该桶内次指标量（使用视角=活跃用户数；任务视角=失败次数；登录视角=活跃用户数；接口视角=错误数）
 * @param failed      该桶内失败/错误数（统计类报表的第二失败口径；使用视角恒为 0）
 */
public record ReportSeriesPointVO(String bucket, long visits, long activeUsers, long failed) {
}
