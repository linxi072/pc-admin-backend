package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
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

    @Test
    void shouldResolveScheduledStatus() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 10, 0);
        LocalDateTime future = now.plusDays(1);
        LocalDateTime past = now.minusDays(1);
        assertEquals("SCHEDULED", CmsArticleRules.resolveInitialStatus("DRAFT", future, now));
        assertEquals("SCHEDULED", CmsArticleRules.resolveInitialStatus(null, future, now));
        assertEquals("DRAFT", CmsArticleRules.resolveInitialStatus("draft", past, now));
        assertEquals("PUBLISHED", CmsArticleRules.resolveInitialStatus("published", future, now));
        assertEquals("DRAFT", CmsArticleRules.resolveInitialStatus("draft", null, now));
    }

    @Test
    void shouldDetectDueForPublish() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 10, 0);
        assertTrue(CmsArticleRules.isDueForPublish("SCHEDULED", now.minusMinutes(1), now));
        assertTrue(CmsArticleRules.isDueForPublish("SCHEDULED", now, now));
        assertFalse(CmsArticleRules.isDueForPublish("SCHEDULED", now.plusMinutes(1), now));
        assertFalse(CmsArticleRules.isDueForPublish("DRAFT", now.minusMinutes(1), now));
        assertFalse(CmsArticleRules.isDueForPublish("SCHEDULED", null, now));
    }
}
