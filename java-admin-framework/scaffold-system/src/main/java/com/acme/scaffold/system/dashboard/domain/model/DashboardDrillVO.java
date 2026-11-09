package com.acme.scaffold.system.dashboard.domain.model;

import java.util.List;

/**
 * 运营看板下钻响应：指定指标在指定日期的逐小时分布。
 *
 * @param metric 下钻指标（原样回显，如 {@code LOGIN_LOGS}）
 * @param date   下钻日期（ISO 本地日期字符串，如 {@code 2026-10-12}）
 * @param title  下钻标题（中文，前端直接展示于抽屉头部）
 * @param series 逐小时序列（0–23 点，缺数据补零，前端直接渲染柱状/折线）
 */
public record DashboardDrillVO(String metric, String date, String title, List<HourlyPoint> series) {
}
