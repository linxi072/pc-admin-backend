package com.acme.scaffold.codegen.service;

import com.acme.scaffold.codegen.dto.ColumnMetaVO;
import com.acme.scaffold.codegen.dto.TableMetaVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 表结构读取服务：从当前库 information_schema 拉取表清单与字段元信息。
 *
 * <p>使用 {@code DATABASE()} 限定当前连接库，避免跨库串表；SQL 为原生查询
 * （information_schema 为标准系统视图，走 DSL 动态拼装无收益且需处理关键字转义）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TableSchemaService {

    private final DSLContext dsl;

    /** 库内业务表清单（排除视图，按表名排序）。 */
    public List<String> listTables() {
        return dsl.resultQuery(
                        "SELECT TABLE_NAME FROM information_schema.TABLES "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_TYPE = 'BASE TABLE' "
                                + "ORDER BY TABLE_NAME")
                .fetch(r -> r.get(0, String.class));
    }

    /**
     * 表字段元信息（含 Java 映射结果）。表不存在时返回 null（由上层转 404）。
     */
    public TableMetaVO tableMeta(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        String comment = dsl.resultQuery(
                        "SELECT TABLE_COMMENT FROM information_schema.TABLES "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?", tableName)
                .fetchOne(r -> r.get(0, String.class));
        if (comment == null) {
            return null;
        }
        List<ColumnMetaVO> columns = new ArrayList<>();
        dsl.resultQuery(
                        "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_TYPE, COLUMN_COMMENT, COLUMN_KEY, IS_NULLABLE, EXTRA "
                                + "FROM information_schema.COLUMNS "
                                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? "
                                + "ORDER BY ORDINAL_POSITION", tableName)
                .forEach(r -> {
                    String dataType = r.get("DATA_TYPE", String.class);
                    String columnType = r.get("COLUMN_TYPE", String.class);
                    boolean tinyintOne = "tinyint".equalsIgnoreCase(dataType)
                            && columnType != null && columnType.toLowerCase().contains("(1)");
                    String extra = r.get("EXTRA", String.class);
                    columns.add(new ColumnMetaVO(
                            r.get("COLUMN_NAME", String.class),
                            dataType,
                            JavaTypeMapper.toJavaType(dataType, tinyintOne),
                            JavaTypeMapper.toJavaField(r.get("COLUMN_NAME", String.class)),
                            r.get("COLUMN_COMMENT", String.class),
                            "PRI".equalsIgnoreCase(r.get("COLUMN_KEY", String.class)),
                            "YES".equalsIgnoreCase(r.get("IS_NULLABLE", String.class)),
                            extra != null && extra.toLowerCase().contains("auto_increment")));
                });
        return new TableMetaVO(tableName, JavaTypeMapper.toClassName(tableName), comment, columns);
    }
}
