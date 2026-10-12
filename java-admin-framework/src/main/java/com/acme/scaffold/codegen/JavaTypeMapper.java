package com.acme.scaffold.codegen;

import java.util.Locale;
import java.util.Set;

/**
 * JDBC 类型 → Java 类型映射纯函数（information_schema DATA_TYPE → 生成的源码类型）。
 *
 * <p>规则按框架常用字段制定，与 Spring 及 DB 解耦，便于离线单测：
 * <ul>
 *   <li>整数：TINYINT(1) → Boolean，其余 TINYINT/SMALLINT → Integer，MEDIUMINT/INT → Integer，BIGINT → Long；</li>
 *   <li>小数：DECIMAL/NUMERIC → BigDecimal；FLOAT/DOUBLE → Double；</li>
 *   <li>字符串：CHAR/VARCHAR/TEXT 系列 → String；</li>
 *   <li>时间：DATE → LocalDate，TIME → LocalTime，DATETIME/TIMESTAMP → LocalDateTime；</li>
 *   <li>二进制 → byte[]；未知类型回退 String。</li>
 * </ul>
 */
public final class JavaTypeMapper {

    private JavaTypeMapper() {
    }

    /** 需要 import 的非常量类型（映射结果命中时收集）。 */
    private static final Set<String> IMPORTED = Set.of(
            "BigDecimal", "LocalDate", "LocalTime", "LocalDateTime");

    /**
     * @param dataType information_schema.COLUMNS.DATA_TYPE（如 varchar / bigint / tinyint）
     * @param isTinyintOne TINYINT 且 COLUMN_TYPE 含 (1) 时传 true（布尔语义）
     * @return Java 简单类型名；未知类型回退 String
     */
    public static String toJavaType(String dataType, boolean isTinyintOne) {
        if (dataType == null || dataType.isBlank()) {
            return "String";
        }
        String t = dataType.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "tinyint":
                return isTinyintOne ? "Boolean" : "Integer";
            case "smallint", "mediumint", "int", "integer":
                return "Integer";
            case "bigint":
                return "Long";
            case "decimal", "numeric":
                return "BigDecimal";
            case "float":
                return "Float";
            case "double", "real":
                return "Double";
            case "char", "varchar", "tinytext", "text", "mediumtext", "longtext", "json", "enum", "set":
                return "String";
            case "date":
                return "LocalDate";
            case "time":
                return "LocalTime";
            case "datetime", "timestamp":
                return "LocalDateTime";
            case "binary", "varbinary", "blob", "tinyblob", "mediumblob", "longblob":
                return "byte[]";
            default:
                return "String";
        }
    }

    /** 该 Java 类型是否需要 import（java.math / java.time 系列）。 */
    public static boolean needsImport(String javaType) {
        return IMPORTED.contains(javaType);
    }

    /**
     * 列名 → Java 字段名（下划线转小驼峰）。
     * {@code user_name} → {@code userName}；{@code id} → {@code id}；null/空回退 {@code field}。
     */
    public static String toJavaField(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return "field";
        }
        String[] parts = columnName.trim().toLowerCase(Locale.ROOT).split("_+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) {
                continue;
            }
            if (i == 0 || sb.length() == 0) {
                sb.append(parts[i]);
            } else {
                sb.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
            }
        }
        return sb.length() == 0 ? "field" : sb.toString();
    }

    /**
     * 表名 → 类名（大驼峰，去掉 {@code sys_} 等常见前缀）。
     * {@code sys_user} → {@code User}；{@code biz_order} → {@code BizOrder}；空回退 {@code Entity}。
     */
    public static String toClassName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "Entity";
        }
        String t = tableName.trim().toLowerCase(Locale.ROOT);
        if (t.startsWith("sys_")) {
            t = t.substring(4);
        } else if (t.startsWith("t_")) {
            t = t.substring(2);
        }
        String field = toJavaField(t);
        if (field.isEmpty()) {
            return "Entity";
        }
        return Character.toUpperCase(field.charAt(0)) + field.substring(1);
    }
}
