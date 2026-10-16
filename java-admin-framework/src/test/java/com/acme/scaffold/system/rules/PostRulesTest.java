package com.acme.scaffold.system.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PostRules} 单元测试：编码形态、字段规范化、用户岗位集合归一。
 * 纯函数离线验证，不依赖 Spring 或数据库。
 */
class PostRulesTest {

    @Test
    void normalizeCodeTrimsAndNullifiesBlank() {
        assertEquals("GM-001", PostRules.normalizeCode("  GM-001  "));
        assertEquals("abc_1", PostRules.normalizeCode("abc_1"));
        assertEquals(null, PostRules.normalizeCode(null));
        assertEquals(null, PostRules.normalizeCode("   "));
        assertEquals(null, PostRules.normalizeCode(""));
    }

    @Test
    void isValidCodeMatchesAllowedChars() {
        assertTrue(PostRules.isValidCode("GM-001"));
        assertTrue(PostRules.isValidCode("ceo_2024"));
        assertTrue(PostRules.isValidCode("dev1"));
        assertFalse(PostRules.isValidCode(null));
        assertFalse(PostRules.isValidCode(""));
        assertFalse(PostRules.isValidCode("has space"));
        assertFalse(PostRules.isValidCode("comma,"));
        assertFalse(PostRules.isValidCode("semi;"));
        assertTrue(PostRules.isValidCode("  GM-001  ")); // 内部先规范化（去首尾空白）后校验
    }

    @Test
    void normalizeNameTrimsAndNullifiesBlank() {
        assertEquals("总经理", PostRules.normalizeName(" 总经理 "));
        assertEquals(null, PostRules.normalizeName(null));
        assertEquals(null, PostRules.normalizeName(""));
    }

    @Test
    void normalizePostIdsDedupesKeepsOrderAndDropsNull() {
        assertEquals(List.of(), PostRules.normalizePostIds(null));
        assertEquals(List.of(), PostRules.normalizePostIds(List.of()));
        assertEquals(List.of(1L, 2L, 3L),
                PostRules.normalizePostIds(java.util.Arrays.asList(1L, 2L, 2L, null, 3L, null)));
    }

    @Test
    void normalizePostIdsRejectsOverLimit() {
        List<Long> tooMany = new java.util.ArrayList<>();
        for (long i = 1; i <= PostRules.MAX_USER_POSTS + 1; i++) {
            tooMany.add(i);
        }
        assertThrows(IllegalArgumentException.class, () -> PostRules.normalizePostIds(tooMany));
        // 临界值不抛异常
        List<Long> atLimit = new java.util.ArrayList<>();
        for (long i = 1; i <= PostRules.MAX_USER_POSTS; i++) {
            atLimit.add(i);
        }
        assertEquals(PostRules.MAX_USER_POSTS, PostRules.normalizePostIds(atLimit).size());
    }
}
