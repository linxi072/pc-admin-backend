package com.acme.scaffold.cms.category.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 栏目规则单测（C1/C2）：编码规范、层级归一、删除引用判定。 */
class CmsCategoryRulesTest {

    @Test
    void shouldNormalizeAndValidateCode() {
        assertEquals("news", CmsCategoryRules.normalizeCode("  NEWS  "));
        assertEquals("", CmsCategoryRules.normalizeCode(null));
        assertTrue(CmsCategoryRules.validCode("news"));
        assertTrue(CmsCategoryRules.validCode("product-2026"));
        assertFalse(CmsCategoryRules.validCode("1news"));
        assertTrue(CmsCategoryRules.validCode("ab"));        // 长度下限 2
        assertFalse(CmsCategoryRules.validCode("news_2026"));
        assertFalse(CmsCategoryRules.validCode("x".repeat(65)));
    }

    @Test
    void shouldNormalizeParentId() {
        assertNull(CmsCategoryRules.normalizeParentId(null));
        assertNull(CmsCategoryRules.normalizeParentId(0L));
        assertEquals(5L, CmsCategoryRules.normalizeParentId(5L));
        assertTrue(CmsCategoryRules.isSelfParent(3L, 3L));
        assertFalse(CmsCategoryRules.isSelfParent(3L, 4L));
        assertFalse(CmsCategoryRules.isSelfParent(null, 3L));
    }

    @Test
    void shouldDecideDelete() {
        assertTrue(CmsCategoryRules.canDelete(0, 0));
        assertFalse(CmsCategoryRules.canDelete(1, 0));
        assertFalse(CmsCategoryRules.canDelete(0, 1));
        assertFalse(CmsCategoryRules.canDelete(2, 3));
    }

    @Test
    void shouldBuildRejectMessage() {
        assertEquals("", CmsCategoryRules.deleteRejectMessage(0, 0));
        assertTrue(CmsCategoryRules.deleteRejectMessage(2, 0).contains("2 个子栏目"));
        assertTrue(CmsCategoryRules.deleteRejectMessage(0, 3).contains("3 篇文章"));
        String both = CmsCategoryRules.deleteRejectMessage(1, 2);
        assertTrue(both.contains("1 个子栏目") && both.contains("2 篇文章"));
    }

    @Test
    void shouldNormalizeCodeCaseInsensitive() {
        assertTrue(CmsCategoryRules.validCode("  NEWS  "));
        assertEquals("news", CmsCategoryRules.normalizeCode("News"));
        assertFalse(CmsCategoryRules.validCode("A"));
        assertFalse(CmsCategoryRules.validCode("-news"));
    }

    @Test
    void shouldKeepNegativeParentIdUnchanged() {
        assertEquals(-1L, CmsCategoryRules.normalizeParentId(-1L));
        assertNull(CmsCategoryRules.normalizeParentId(0L));
        assertFalse(CmsCategoryRules.isSelfParent(null, null));
    }
}
