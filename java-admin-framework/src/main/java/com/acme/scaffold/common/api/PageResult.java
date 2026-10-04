package com.acme.scaffold.common.api;

import java.util.List;

/**
 * 通用分页出参。
 */
public record PageResult<T>(long page, long size, long total, List<T> records) {

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(page, size, 0L, List.of());
    }

    /** 由记录列表与总数构造分页结果（jOOQ 查询后手动组装）。 */
    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        return new PageResult<>(page, size, total, records);
    }
}
