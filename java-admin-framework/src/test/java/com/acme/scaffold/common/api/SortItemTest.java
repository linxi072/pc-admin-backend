package com.acme.scaffold.common.api;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SortItem} 单元测试：字段名 + 方向解析，方向非法必须 400 而非静默降级。
 */
class SortItemTest {

    @Test
    void constantsAndDefaults() {
        assertEquals("ASC", SortItem.ASC);
        assertEquals("DESC", SortItem.DESC);
    }

    @Test
    void singleBlankFieldReturnsEmpty() {
        assertTrue(SortItem.single(null, "ASC").isEmpty());
        assertTrue(SortItem.single("  ", "DESC").isEmpty());
    }

    @Test
    void singleNullDirectionDefaultsAsc() {
        List<SortItem> items = SortItem.single("username", null);
        assertEquals(1, items.size());
        assertEquals("username", items.get(0).field());
        assertEquals("ASC", items.get(0).direction());
        assertTrue(items.get(0).ascending());
    }

    @Test
    void singleDesc() {
        SortItem item = SortItem.single("username", "desc").get(0);
        assertEquals("DESC", item.direction());
        assertFalse(item.ascending());
    }

    @Test
    void singleInvalidDirectionThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> SortItem.single("username", "SIDEWAYS"));
        assertTrue(ex.getMessage().contains("ASC/DESC"), ex.getMessage());
    }
}
