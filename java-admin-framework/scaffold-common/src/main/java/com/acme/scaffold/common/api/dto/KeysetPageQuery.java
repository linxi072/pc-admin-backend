package com.acme.scaffold.common.api.dto;

import com.acme.scaffold.common.api.CursorCodec;

import java.util.List;

/**
 * keyset（游标）分页入参。与 {@link PageQuery} 的 offset 分页不同，
 * keyset 分页使用「上一页最后一条的排序键游标」定位，深翻页性能稳定（不随偏移量退化）。
 *
 * <p>排序键由服务端固定（见各 Service 的 keyset 实现），客户端仅传 size 与 cursor，
 * 不直接控制排序字段，避免 SQL 注入与不稳定排序。
 */
public record KeysetPageQuery(int size, String cursor) {

    public static final int MAX_SIZE = 100;

    public KeysetPageQuery {
        size = Math.min(Math.max(size, 1), MAX_SIZE);
        if (cursor == null) {
            cursor = "";
        }
    }

    /** 构造首页查询（游标为空）。 */
    public static KeysetPageQuery of(int size) {
        return new KeysetPageQuery(size, "");
    }

    public int limit() {
        return size;
    }

    /** 解码游标为排序键值（空列表表示首页）。 */
    public List<String> decodedCursor() {
        return CursorCodec.decode(cursor);
    }
}
