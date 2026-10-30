package com.acme.scaffold.codegen.dto;

/**
 * 表字段元信息（information_schema.COLUMNS 投影 + Java 映射结果）。
 *
 * @param columnName   列名（如 user_name）
 * @param dataType     JDBC 数据类型（如 varchar / bigint）
 * @param javaType     映射后的 Java 类型（如 String / Long）
 * @param javaField    映射后的 Java 字段名（如 userName）
 * @param comment      列注释（可能为空）
 * @param primaryKey   是否主键
 * @param nullable     是否可空（YES → true）
 * @param autoIncrement 是否自增
 */
public record ColumnMetaVO(String columnName, String dataType, String javaType, String javaField,
                           String comment, boolean primaryKey, boolean nullable, boolean autoIncrement) {
}
