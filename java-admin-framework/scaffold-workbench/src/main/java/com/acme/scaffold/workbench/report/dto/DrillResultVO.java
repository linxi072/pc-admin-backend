package com.acme.scaffold.workbench.report.dto;

import java.util.List;

/**
 * 报表下钻响应。
 *
 * <p>在 {@link ReportVO} 既有的聚合契约之外，为「点击下钻」场景提供轻量返回：
 * 时间序列 {@code series} 复用 {@link ReportSeriesPointVO}（逐小时或按天，取决于下钻维度），
 * 明细表 {@code details} 仅在按耗时分桶下钻（JOB_EXEC）时填充，其余维度为空表。</p>
 *
 * @param dimension 下钻维度（原样回显，便于前端判断渲染形态）
 * @param parent    父级取值（原样回显，如某天 {@code "10-12"} / 某模块编码 / 某状态码）
 * @param title     下钻标题（中文，前端直接展示于抽屉头部）
 * @param rangeType 本次查询范围类型
 * @param start     解析后的窗口起始时间（ISO 本地时间字符串）
 * @param end       解析后的窗口结束时间（ISO 本地时间字符串）
 * @param series    下钻后的时间序列（逐小时或按天）
 * @param details   下钻明细表（耗时分桶下钻时填充，其余维度为空表）
 */
public record DrillResultVO(String dimension, String parent, String title,
                           String rangeType, String start, String end,
                           List<ReportSeriesPointVO> series,
                           ReportDetailTableVO details) {
}
