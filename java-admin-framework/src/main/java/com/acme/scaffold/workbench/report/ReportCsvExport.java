package com.acme.scaffold.workbench.report;

/**
 * CSV 导出载荷（文件名 + UTF-8 字节内容，含 BOM 以便 Excel 直接打开）。
 *
 * @param filename 下载文件名（含 .csv 后缀）
 * @param content  CSV 字节内容（UTF-8 + BOM）
 */
public record ReportCsvExport(String filename, byte[] content) {
}
