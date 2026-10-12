package com.acme.scaffold.workbench.report;

import java.util.List;

/**
 * 报表完整响应。
 *
 * <p>字段口径：{@code summary} 为窗口汇总；{@code trend} 为按时间桶排列的完整连续序列
 * （缺失桶补零，前端直接渲染折线/面积图，无需再补点）；{@code categoryStats} 为按类别聚合的
 * Top10（按 {@code sortBy/order} 排序）；{@code statusStats} 为成功/失败分布。
 * {@code type/title/rangeType/start/end} 回显本次查询，便于前端回填筛选器。</p>
 *
 * <p>{@code distribution} 与 {@code details} 为统计类报表（JOB_EXEC / LOGIN / API_CALL）的扩展维度：
 * 分布分桶（耗时/响应时间/登录类型）与明细表（最近执行/异常登录/Top 接口）；使用视角下为空。</p>
 *
 * @param type          报表类型（USAGE / CATEGORY / STATUS / JOB_EXEC / LOGIN / API_CALL）
 * @param title         报表标题（中文，前端直接展示）
 * @param rangeType     本次查询的范围类型（TODAY / LAST_7D / LAST_30D / CUSTOM）
 * @param start         解析后的窗口起始时间（ISO 本地时间字符串）
 * @param end           解析后的窗口结束时间（ISO 本地时间字符串）
 * @param summary       汇总指标
 * @param trend         连续时间趋势序列（按时间升序）
 * @param categoryStats 类别（模块）使用频次 Top10（按 sortBy/order 排序）
 * @param statusStats   状态分布（成功 / 失败）
 * @param distribution  分布分桶（耗时分布 / 响应时间分布 / 登录类型分布；使用视角为空表）
 * @param details       明细表（最近执行 / 异常登录 / Top 接口；使用视角为空表）
 */
public record ReportVO(String type, String title, String rangeType, String start, String end,
                       ReportSummaryVO summary,
                       List<ReportSeriesPointVO> trend,
                       List<ReportCategoryStatVO> categoryStats,
                       List<ReportStatusStatVO> statusStats,
                       List<ReportDistributionVO> distribution,
                       ReportDetailTableVO details) {
}
