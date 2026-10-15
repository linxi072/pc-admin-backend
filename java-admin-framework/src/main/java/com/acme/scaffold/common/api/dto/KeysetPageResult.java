package com.acme.scaffold.common.api.dto;

import java.util.List;

/**
 * keyset 分页出参。不携带 total（keyset 不廉价计算总数），以 {@link #nextCursor()} 表示是否还有下一页。
 */
public record KeysetPageResult<T>(long size, List<T> records, String nextCursor) {

    public static <T> KeysetPageResult<T> of(long size, List<T> records, String nextCursor) {
        return new KeysetPageResult<>(size, records == null ? List.of() : records, nextCursor);
    }

    /** 是否还有下一页。 */
    public boolean hasNext() {
        return nextCursor != null && !nextCursor.isBlank();
    }
}
