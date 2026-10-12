package com.acme.scaffold.codegen.dto;

import java.util.List;

/**
 * 表结构元信息（字段列表 + 推导出的类名）。
 *
 * @param tableName 表名（如 sys_user）
 * @param className  推导类名（如 User）
 * @param comment   表注释（可能为空）
 * @param columns   字段列表（按 ORDINAL_POSITION 升序）
 */
public record TableMetaVO(String tableName, String className, String comment, List<ColumnMetaVO> columns) {
}
