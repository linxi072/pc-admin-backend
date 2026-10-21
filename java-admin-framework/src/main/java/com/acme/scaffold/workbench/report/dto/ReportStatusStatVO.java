package com.acme.scaffold.workbench.report.dto;

/**
 * 状态分布单点（成功 / 失败）。
 *
 * @param statusLabel 状态中文标签（成功 / 失败）
 * @param count       该状态的操作次数
 * @param ratio       占全部操作次数的百分比（保留 1 位小数）
 */
public record ReportStatusStatVO(String statusLabel, long count, double ratio) {
}
