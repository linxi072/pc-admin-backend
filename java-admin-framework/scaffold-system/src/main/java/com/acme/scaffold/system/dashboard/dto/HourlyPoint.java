package com.acme.scaffold.system.dashboard.dto;

/**
 * 逐小时统计点（看板下钻：一个指标在指定日期的 0–23 点分布）。
 *
 * @param hour  小时（0–23）
 * @param value 该小时内的计数值
 */
public record HourlyPoint(int hour, long value) {
}
