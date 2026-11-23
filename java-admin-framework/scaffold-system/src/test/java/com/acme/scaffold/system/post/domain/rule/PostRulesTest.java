package com.acme.scaffold.system.post.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 岗位管理纯规则单测（TEST-COVERAGE-PLAN 第二批 / T3 system 主链路）。
 * 锁定编码形态校验、字段规范化与岗位集合去重/上限契约。
 */
class PostRulesTest {

    @Test
    void normalizeCodeShouldTrimAndNullOutBlank() {
        assertNull(PostRules.normalizeCode(null));
        assertNull(PostRules.normalizeCode("   "));
        assertEquals("DEV-01", PostRules.normalizeCode("  DEV-01  "));
    }

    @Test
    void isValidCodeShouldAcceptLettersDigitsUnderscoreHyphen() {
        assertTrue(PostRules.isValidCode("DEV_01"));
        assertTrue(PostRules.isValidCode("总经理-001"));
        assertTrue(PostRules.isValidCode("  A1_B-2  "), "trim 后校验");
        assertFalse(PostRules.isValidCode(null));
        assertFalse(PostRules.isValidCode("   "));
        assertFalse(PostRules.isValidCode("has space"));
        assertFalse(PostRules.isValidCode("code@x"));
    }

    @Test
    void normalizeNameShouldTrimAndNullOutBlank() {
        assertNull(PostRules.normalizeName(null));
        assertNull(PostRules.normalizeName("  "));
        assertEquals("研发工程师", PostRules.normalizeName("  研发工程师  "));
    }

    @Test
    void normalizePostIdsShouldFilterNullAndDedupKeepingOrder() {
        assertEquals(List.of(), PostRules.normalizePostIds(null));
        assertEquals(List.of(), PostRules.normalizePostIds(List.of()));
        assertEquals(List.of(3L, 1L, 2L),
                PostRules.normalizePostIds(Arrays.asList(3L, null, 1L, 3L, 2L, null)));
    }

    @Test
    void normalizePostIdsShouldThrowWhenExceedingMax() {
        List<Long> tooMany = java.util.stream.LongStream.rangeClosed(1, PostRules.MAX_USER_POSTS + 1)
                .boxed().toList();
        assertThrows(IllegalArgumentException.class, () -> PostRules.normalizePostIds(tooMany));
        // 恰好上限通过
        List<Long> atMax = java.util.stream.LongStream.rangeClosed(1, PostRules.MAX_USER_POSTS)
                .boxed().toList();
        assertEquals(PostRules.MAX_USER_POSTS, PostRules.normalizePostIds(atMax).size());
    }

    @Test
    void normalizePostIdsShouldCountAfterDedup() {
        // 11 个元素但去重后只有 2 个，不应因原始数量超限而抛错
        List<Long> duplicated = java.util.stream.Stream.generate(() -> 1L)
                .limit(PostRules.MAX_USER_POSTS + 1).toList();
        List<Long> withTwo = new java.util.ArrayList<>(duplicated);
        withTwo.add(2L);
        assertEquals(2, PostRules.normalizePostIds(withTwo).size());
    }
}
