package com.acme.scaffold.common.importer;

/**
 * 单行导入失败明细。
 *
 * @param rowNo   数据行号（从 1 开始，不含表头；与用户在 Excel/CSV 中的物理行号相差 1）
 * @param message 失败原因（多字段校验失败时以「；」连接，便于前端直接展示）
 */
public record RowError(int rowNo, String message) {
}
