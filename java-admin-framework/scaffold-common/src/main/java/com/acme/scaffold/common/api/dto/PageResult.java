package com.acme.scaffold.common.api.dto;

import java.util.List;

/**
 * 通用分页出参。
 */
public record PageResult<T>(long page, long size, long total, List<T> records) {

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(page, size, 0L, List.of());
    }

    /**
     * 由记录列表与总数构造分页结果（jOOQ 查询后手动组装）。
     *
     * <p>与 {@link KeysetPageResult#of(long, List, String)} 保持同一口径：records 为 null 时
     * 归一为空列表，避免响应体出现 {@code "records": null} 而令消费方 {@code .map()} 抛错。
     * 正常路径下调用方总是传入非 null 列表，故不改变既有响应。</p>
     */
    public static <T> PageResult<T> of(long page, long size, long total, List<T> records) {
        return new PageResult<>(page, size, total, records == null ? List.of() : records);
    }
}
