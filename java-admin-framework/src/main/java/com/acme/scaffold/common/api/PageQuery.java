package com.acme.scaffold.common.api;

import java.util.List;

/**
 * 通用分页查询入参。分页参数在构造时做边界保护，避免越界与超大页。
 */
public record PageQuery(int page, int size, List<SortItem> sorts) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        if (sorts == null) {
            sorts = List.of();
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size, List.of());
    }

    /** 带扁平排序参数（sortField / sortDirection）的分页查询，方向非法时由 {@link SortItem#single} 抛 400。 */
    public static PageQuery of(int page, int size, String sortField, String sortDirection) {
        return new PageQuery(page, size, SortItem.single(sortField, sortDirection));
    }

    /** 计算 SQL 偏移量（offset）。排序字段由服务端白名单映射，禁止直接使用客户端字段名。 */
    public long offset() {
        return (long) (page - 1) * size;
    }

    public int limit() {
        return size;
    }
}
