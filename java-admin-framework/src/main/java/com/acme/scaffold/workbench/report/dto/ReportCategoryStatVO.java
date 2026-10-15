package com.acme.scaffold.workbench.report.dto;

/**
 * 功能（模块）使用频次（按 module_code 聚合的 Top N）。
 *
 * @param moduleCode 模块编码（如 user / workflow），为空时记为 未知
 * @param count      该模块在统计窗口内的操作次数
 * @param ratio      占全部操作次数的百分比（保留 1 位小数；总量为 0 时记 0.0）
 */
public record ReportCategoryStatVO(String moduleCode, long count, double ratio) {
}
