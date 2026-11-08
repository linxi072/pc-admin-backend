package com.acme.scaffold.ai.service;

import com.acme.scaffold.ai.dto.ColumnSpec;
import com.acme.scaffold.ai.dto.TableSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 迁移 SQL 渲染器：表规格 → 带引号小写 snake_case 标识符的 CREATE TABLE + 独立 CREATE INDEX。
 *
 * <p>对齐本仓 Flyway 迁移约定（V74/V76）：标识符显式加双引号保留 snake_case 小写，
 * 索引用独立语句，索引名以 {@code idx_<表>_<列>} 全局唯一；tenant_id 按框架约定
 * NOT NULL DEFAULT 0 承载租户隔离。</p>
 */
public final class DdlRenderer {

    private DdlRenderer() {
    }

    /** 参与独立索引的标准列（tenant_id / status / code / category）。 */
    private static final List<String> INDEX_COLUMNS = List.of("tenant_id", "status", "code", "category");

    public static String render(TableSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE \"").append(spec.tableName()).append("\" (\n");

        List<String> colLines = new ArrayList<>();
        for (ColumnSpec c : spec.columns()) {
            colLines.add("    \"" + c.columnName() + "\" " + jdbcType(c) + " " + columnAttrs(c));
        }
        sb.append(String.join(",\n", colLines));
        sb.append(",\n    PRIMARY KEY (\"").append(pkName(spec)).append("\")\n);\n");

        for (ColumnSpec c : spec.columns()) {
            if (c.primaryKey() || !INDEX_COLUMNS.contains(c.columnName())) {
                continue;
            }
            sb.append("CREATE INDEX \"idx_").append(spec.tableName()).append("_")
                    .append(c.columnName()).append("\" ON \"").append(spec.tableName())
                    .append("\" (\"").append(c.columnName()).append("\");\n");
        }
        return sb.toString();
    }

    private static String pkName(TableSpec spec) {
        return spec.columns().stream().filter(ColumnSpec::primaryKey)
                .map(ColumnSpec::columnName).findFirst().orElse("id");
    }

    private static String jdbcType(ColumnSpec c) {
        return switch (c.dataType().toLowerCase()) {
            case "decimal" -> "DECIMAL(10,2)";
            case "varchar" -> "VARCHAR(255)";
            case "text" -> "TEXT";
            case "datetime" -> "DATETIME";
            case "tinyint" -> "TINYINT(1)";
            case "bigint" -> "BIGINT";
            case "int" -> "INT";
            default -> "VARCHAR(255)";
        };
    }

    private static String columnAttrs(ColumnSpec c) {
        if ("tenant_id".equals(c.columnName())) {
            return "NOT NULL DEFAULT 0 COMMENT '租户ID'";
        }
        if (c.primaryKey()) {
            return "NOT NULL AUTO_INCREMENT";
        }
        List<String> attrs = new ArrayList<>();
        attrs.add(c.nullable() ? "NULL" : "NOT NULL");
        if (!c.nullable() && "tinyint".equals(c.dataType().toLowerCase())) {
            attrs.add("DEFAULT 0");
        }
        if (c.comment() != null && !c.comment().isBlank()) {
            attrs.add("COMMENT '" + c.comment() + "'");
        }
        return String.join(" ", attrs);
    }
}
