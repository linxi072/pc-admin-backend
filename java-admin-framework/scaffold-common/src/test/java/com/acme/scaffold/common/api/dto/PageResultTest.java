package com.acme.scaffold.common.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link PageResult} 单测：与 {@link KeysetPageResult#of} 对齐的 null records 归一口径。
 */
class PageResultTest {

    @Test
    @DisplayName("of：records 为 null 时归一为空列表，避免响应体出现 \"records\": null")
    void ofNormalizesNullRecords() {
        PageResult<String> page = PageResult.of(1, 20, 0L, null);
        assertNotNull(page.records());
        assertEquals(List.of(), page.records());
        assertEquals(1L, page.page());
        assertEquals(0L, page.total());
    }

    @Test
    @DisplayName("of：非 null 列表原样保留（不改变既有响应与对象身份）")
    void ofKeepsGivenRecords() {
        List<String> rows = List.of("a", "b");
        assertSame(rows, PageResult.of(2, 20, 2L, rows).records());
    }

    @Test
    @DisplayName("empty：空页恒为非 null 空列表")
    void emptyHasEmptyRecords() {
        PageResult<String> page = PageResult.empty(1, 10);
        assertEquals(List.of(), page.records());
        assertEquals(0L, page.total());
    }
}
