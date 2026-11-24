package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 文章规则单测（C1）：栏目引用、作者回退、标题、关键字、排序白名单。 */
class CmsArticleRulesTest {

    @Test
    void shouldValidateCategoryId() {
        assertFalse(CmsArticleRules.validCategoryId(null));
        assertFalse(CmsArticleRules.validCategoryId(0L));
        assertFalse(CmsArticleRules.validCategoryId(-1L));
        assertTrue(CmsArticleRules.validCategoryId(1L));
    }

    @Test
    void shouldResolveAuthor() {
        assertEquals("alice", CmsArticleRules.resolveAuthor("alice", "system"));
        assertEquals("system", CmsArticleRules.resolveAuthor("  ", "system"));
        assertEquals("system", CmsArticleRules.resolveAuthor(null, "system"));
    }

    @Test
    void shouldValidateTitle() {
        assertEquals("hello", CmsArticleRules.normalizeTitle("  hello  "));
        assertEquals("", CmsArticleRules.normalizeTitle(null));
        assertTrue(CmsArticleRules.validTitle("  title  "));
        assertFalse(CmsArticleRules.validTitle("   "));
        assertFalse(CmsArticleRules.validTitle("x".repeat(256)));
    }

    @Test
    void shouldBuildLikePattern() {
        assertNull(CmsArticleRules.likePattern(null));
        assertNull(CmsArticleRules.likePattern("  "));
        assertEquals("%key%", CmsArticleRules.likePattern(" key "));
    }

    @Test
    void shouldValidateSortFieldAndDirection() {
        assertTrue(CmsArticleRules.validSortField(null));
        assertTrue(CmsArticleRules.validSortField("viewCount"));
        assertTrue(CmsArticleRules.validSortField("VIEWCOUNT"));
        assertFalse(CmsArticleRules.validSortField("secret"));
        assertEquals("desc", CmsArticleRules.normalizeDirection(null));
        assertEquals("asc", CmsArticleRules.normalizeDirection(" ASC "));
        assertEquals("desc", CmsArticleRules.normalizeDirection("bogus"));
    }

    @Test
    void shouldAcceptTitleAtMaxLength() {
        assertTrue(CmsArticleRules.validTitle("x".repeat(255)));
        assertFalse(CmsArticleRules.validTitle("x".repeat(256)));
        assertEquals("", CmsArticleRules.normalizeTitle("   "));
    }

    @Test
    void shouldKeepInnerSpacesInLikePattern() {
        assertEquals("%foo bar%", CmsArticleRules.likePattern(" foo bar "));
        assertEquals("%a%b%", CmsArticleRules.likePattern("a%b"));
    }
}
