package com.acme.scaffold.workbench.report;

/**
 * 报表类型元数据（供前端渲染切换 Tab）。
 *
 * @param code        类型编码（与 {@link ReportType#name()} 一致）
 * @param label       中文标题
 * @param description 类型说明
 */
public record ReportTypeMeta(String code, String label, String description) {
}
