package com.acme.scaffold.common.importer;

/**
 * 单段导入结果汇总（多段导入下 {@link ImportResult#segments()} 的逐项明细）。
 *
 * <p>导入中心逐段独立统计：单段失败不影响其余段，因此 {@link ImportResult} 在聚合总量的同时，
 * 也保留每段的处理行数 / 成功 / 失败，便于前端按工作表逐项展示。</p>
 *
 * @param name           段名（工作表名）
 * @param totalRows      该段实际处理的数据行数（已跳过全空行，不含表头）
 * @param successCount   该段成功行数
 * @param failureCount   该段失败行数（真实值，不受错误明细上限影响）
 */
public record SegmentResult(String name, int totalRows, int successCount, int failureCount) {
}
