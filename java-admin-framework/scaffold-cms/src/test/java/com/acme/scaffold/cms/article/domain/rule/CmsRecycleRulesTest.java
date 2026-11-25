package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 回收站规则单测：ids 归一、上限与空值校验（C9）。 */
class CmsRecycleRulesTest {

    @Test
    void shouldNormalizeIdsInOrder() {
        assertEquals(List.of(3L, 1L, 2L),
                CmsRecycleRules.normalizeIds(Arrays.asList(3L, 1L, 3L, 2L)));
    }

    @Test
    void shouldDropInvalidIds() {
        assertEquals(List.of(1L, 2L),
                CmsRecycleRules.normalizeIds(Arrays.asList(null, 1L, 0L, -1L, 2L, null)));
        assertTrue(CmsRecycleRules.normalizeIds(null).isEmpty());
        assertTrue(CmsRecycleRules.normalizeIds(List.of()).isEmpty());
    }

    @Test
    void shouldValidateIds() {
        assertFalse(CmsRecycleRules.validIds(null));
        assertFalse(CmsRecycleRules.validIds(List.of()));
        assertFalse(CmsRecycleRules.validIds(Arrays.asList(null, 0L, -3L)));
        assertTrue(CmsRecycleRules.validIds(List.of(1L, 2L)));
    }

    @Test
    void shouldEnforceMaxIds() {
        List<Long> atLimit = IntStream.rangeClosed(1, CmsRecycleRules.MAX_IDS)
                .mapToObj(Long::valueOf).toList();
        assertTrue(CmsRecycleRules.validIds(atLimit));
        List<Long> overLimit = IntStream.rangeClosed(1, CmsRecycleRules.MAX_IDS + 1)
                .mapToObj(Long::valueOf).toList();
        assertFalse(CmsRecycleRules.validIds(overLimit));
        // 去重后在上限内仍合法
        List<Long> duplicated = new ArrayList<>(atLimit);
        duplicated.add(1L);
        assertTrue(CmsRecycleRules.validIds(duplicated));
    }
}
