package com.acme.scaffold.ai.dto;

/**
 * 列规格：AI 解析出的字段定义，含 JDBC 类型与 Java 类型映射结果。
 *
 * <p>javaType 由 {@code com.acme.scaffold.codegen.service.JavaTypeMapper} 预先映射，
 * 供 DDL 与代码模板统一复用，避免类型推断逻辑重复。</p>
 *
 * @param columnName    列名（snake_case，如 amount）
 * @param dataType      JDBC 数据类型（如 decimal / varchar / bigint）
 * @param javaType      映射后的 Java 类型（如 BigDecimal / String / Long）
 * @param javaField     映射后的 Java 字段名（如 amount）
 * @param comment       列注释（可能为空）
 * @param primaryKey    是否主键
 * @param nullable      是否可空
 * @param autoIncrement 是否自增
 */
public record ColumnSpec(
        String columnName,
        String dataType,
        String javaType,
        String javaField,
        String comment,
        boolean primaryKey,
        boolean nullable,
        boolean autoIncrement) {
}
