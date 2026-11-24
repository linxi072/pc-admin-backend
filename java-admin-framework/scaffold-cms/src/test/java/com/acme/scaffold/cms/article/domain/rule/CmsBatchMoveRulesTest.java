package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** 批量移动规则单测：ids 归一、上限、目标栏目校验。 */
class CmsBatchMoveRulesTest {

    @Test
    void shouldNormalizeIdsInOrder() {
        assertEquals(List.of(3L, 1L, 2L),
                CmsBatchMoveRules.normalizeIds(Arrays.asList(3L, 1L, 3L, 2L)));
    }

    @Test
    void shouldDropInvalidIds() {
        assertEquals(List.of(1L, 2L),
                CmsBatchMoveRules.normalizeIds(Arrays.asList(null, 1L, 0L, -1L, 2L, null)));
        assertTrue(CmsBatchMoveRules.normalizeIds(null).isEmpty());
        assertTrue(CmsBatchMoveRules.normalizeIds(List.of()).isEmpty());
    }

    @Test
    void shouldValidateIds() {
        assertFalse(CmsBatchMoveRules.validIds(null));
        assertFalse(CmsBatchMoveRules.validIds(List.of()));
        assertFalse(CmsBatchMoveRules.validIds(Arrays.asList(null, 0L, -3L)));
        assertTrue(CmsBatchMoveRules.validIds(List.of(1L, 2L)));
    }

    @Test
    void shouldEnforceMaxIds() {
        List<Long> atLimit = IntStream.rangeClosed(1, CmsBatchMoveRules.MAX_IDS)
                .mapToObj(Long::valueOf).toList();
        assertTrue(CmsBatchMoveRules.validIds(atLimit));
        List<Long> overLimit = IntStream.rangeClosed(1, CmsBatchMoveRules.MAX_IDS + 1)
                .mapToObj(Long::valueOf).toList();
        assertFalse(CmsBatchMoveRules.validIds(overLimit));
        // 去重后在上限内仍合法
        List<Long> duplicated = new java.util.ArrayList<>(atLimit);
        duplicated.add(1L);
        assertTrue(CmsBatchMoveRules.validIds(duplicated));
    }

    @Test
    void shouldValidateTargetCategory() {
        assertTrue(CmsBatchMoveRules.validTargetCategory(1L));
        assertFalse(CmsBatchMoveRules.validTargetCategory(null));
        assertFalse(CmsBatchMoveRules.validTargetCategory(0L));
        assertFalse(CmsBatchMoveRules.validTargetCategory(-1L));
    }
}
