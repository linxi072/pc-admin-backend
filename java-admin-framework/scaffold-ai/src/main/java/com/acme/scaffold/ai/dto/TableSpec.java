package com.acme.scaffold.ai.dto;

import java.util.List;

/**
 * 表规格：自然语言解析出的表结构草案（含推导出的类名与字段列表）。
 *
 * @param tableName 表名（如 sys_order）
 * @param comment   表注释（中文实体名，可能为空）
 * @param columns   字段列表（按声明顺序：主键 → 业务字段 → 审计字段）
 */
public record TableSpec(
        String tableName,
        String comment,
        List<ColumnSpec> columns) {
}
