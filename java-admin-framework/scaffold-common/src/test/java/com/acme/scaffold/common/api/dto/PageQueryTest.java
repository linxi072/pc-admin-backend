package com.acme.scaffold.common.api.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PageQuery} 边界单测。
 *
 * <p>2026-10-19 测试轮次修复：{@code page} 无上限钳制，且分页实现处以 int 乘法计算
 * offset，超大页码会溢出为负数。现 PageQuery 钳制 page 上限、offset 改 long 计算
 * （{@link com.acme.scaffold.jooq.dsl.JooqWriters#page} 已改用 {@link PageQuery#offset()}）。</p>
 */
class PageQueryTest {

    @Test
    @DisplayName("page/size 下界钳制为 1")
    void lowerBoundsClamped() {
        PageQuery q = PageQuery.of(0, -5);
        assertEquals(1, q.page());
        assertEquals(1, q.size());
        assertEquals(0L, q.offset());
    }

    @Test
    @DisplayName("size 上界钳制为 MAX_SIZE=100")
    void sizeUpperBoundClamped() {
        assertEquals(100, PageQuery.of(1, 10_000).size());
    }

    @Test
    @DisplayName("page 上界钳制为 MAX_PAGE=100000")
    void pageUpperBoundClamped() {
        assertEquals(PageQuery.MAX_PAGE, PageQuery.of(Integer.MAX_VALUE, 100).page());
    }

    @Test
    @DisplayName("超大页码被钳制为 MAX_PAGE=100000，offset 非负不溢出（修复前 int 乘法溢出为负）")
    void offsetNeverOverflows() {
        // 修复前 (page-1)*size 为 int 乘法：2147483 * 100 溢出为负 offset
        PageQuery q = PageQuery.of(2_147_483, 100);
        assertEquals(PageQuery.MAX_PAGE, q.page());
        assertEquals(100, q.size());
        assertEquals(99_999L * 100, q.offset());
        assertTrue(q.offset() >= 0, "offset 不允许为负");
    }

    @Test
    @DisplayName("sorts 为 null 时归一为空列表")
    void nullSortsNormalized() {
        assertEquals(List.of(), new PageQuery(1, 10, null).sorts());
    }
}
