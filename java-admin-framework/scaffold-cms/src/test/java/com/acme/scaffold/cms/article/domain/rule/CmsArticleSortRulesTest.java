package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.cms.article.domain.ArticleSortItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 文章排序规则单测：排序号钳制、排序项归一去重与上限（C10）。 */
class CmsArticleSortRulesTest {

    @Test
    void shouldNormalizeSortNo() {
        assertEquals(0, CmsArticleSortRules.normalizeSortNo(null));
        assertEquals(0, CmsArticleSortRules.normalizeSortNo(-5));
        assertEquals(7, CmsArticleSortRules.normalizeSortNo(7));
        assertEquals(CmsArticleSortRules.MAX_SORT_NO,
                CmsArticleSortRules.normalizeSortNo(CmsArticleSortRules.MAX_SORT_NO + 1));
    }

    @Test
    void shouldValidateSortNo() {
        assertFalse(CmsArticleSortRules.validSortNo(null));
        assertFalse(CmsArticleSortRules.validSortNo(-1));
        assertFalse(CmsArticleSortRules.validSortNo(10000));
        assertTrue(CmsArticleSortRules.validSortNo(0));
        assertTrue(CmsArticleSortRules.validSortNo(9999));
    }

    @Test
    void shouldNormalizeItemsDedupeAndClamp() {
        List<ArticleSortItem> raw = Arrays.asList(
                new ArticleSortItem(3L, 5),
                new ArticleSortItem(1L, -2),
                new ArticleSortItem(3L, 99), // 重复 id，保留首次
                new ArticleSortItem(2L, null)); // 非法排序号 → 钳为 0
        List<ArticleSortItem> normalized = CmsArticleSortRules.normalizeItems(raw);
        assertEquals(3, normalized.size());
        assertEquals(new ArticleSortItem(3L, 5), normalized.get(0));
        assertEquals(new ArticleSortItem(1L, 0), normalized.get(1));
        assertEquals(new ArticleSortItem(2L, 0), normalized.get(2));
    }

    @Test
    void shouldDropInvalidItems() {
        List<ArticleSortItem> raw = Arrays.asList(
                null,
                new ArticleSortItem(null, 1),
                new ArticleSortItem(0L, 1),
                new ArticleSortItem(-1L, 1),
                new ArticleSortItem(1L, 2));
        assertEquals(List.of(new ArticleSortItem(1L, 2)), CmsArticleSortRules.normalizeItems(raw));
        assertTrue(CmsArticleSortRules.normalizeItems(null).isEmpty());
        assertTrue(CmsArticleSortRules.normalizeItems(List.of()).isEmpty());
    }

    @Test
    void shouldEnforceMaxItems() {
        List<ArticleSortItem> atLimit = IntStream.rangeClosed(1, CmsArticleSortRules.MAX_ITEMS)
                .mapToObj(i -> new ArticleSortItem((long) i, i)).toList();
        assertTrue(CmsArticleSortRules.validItems(atLimit));
        List<ArticleSortItem> overLimit = new ArrayList<>(atLimit);
        overLimit.add(new ArticleSortItem((long) (CmsArticleSortRules.MAX_ITEMS + 1), 1));
        assertFalse(CmsArticleSortRules.validItems(overLimit));
        assertFalse(CmsArticleSortRules.validItems(List.of()));
    }
}
