package com.acme.scaffold.system.dashboard;

import java.time.LocalDate;

/**
 * 单日趋势点（M27 报表大屏 / 时间序列）：某日期对应的计数值。
 *
 * @param date  统计日期（按天聚合，不含时间）
 * @param value 当日计数值（非负；缺失日由规则补齐为 0）
 */
public record TrendPoint(LocalDate date, long value) {
}
