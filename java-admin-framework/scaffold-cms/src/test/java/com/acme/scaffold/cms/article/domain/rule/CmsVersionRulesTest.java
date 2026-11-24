package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 文章版本规则单测（C4）：版本号分配、回滚判定、内容一致性。 */
class CmsVersionRulesTest {

    @Test
    void shouldAssignNextVersionNo() {
        assertEquals(1, CmsVersionRules.nextVersionNo(List.of()));
        assertEquals(1, CmsVersionRules.nextVersionNo(null));
        assertEquals(2, CmsVersionRules.nextVersionNo(List.of(1)));
        assertEquals(4, CmsVersionRules.nextVersionNo(List.of(1, 3, 2)));
        assertEquals(1, CmsVersionRules.nextVersionNo(Arrays.asList(null, -1)));
    }

    @Test
    void shouldGuardVersionOverflow() {
        assertEquals(Integer.MAX_VALUE,
                CmsVersionRules.nextVersionNo(List.of(Integer.MAX_VALUE)));
    }

    @Test
    void shouldDecideRollback() {
        assertTrue(CmsVersionRules.canRollback(3, 1));
        assertFalse(CmsVersionRules.canRollback(3, 3));
        assertFalse(CmsVersionRules.canRollback(null, 1));
        assertFalse(CmsVersionRules.canRollback(3, null));
    }

    @Test
    void shouldCompareContentIgnoringNull() {
        assertTrue(CmsVersionRules.sameContent("t", "s", "c", "t", "s", "c"));
        assertTrue(CmsVersionRules.sameContent(null, "s", "c", "", "s", "c"));
        assertFalse(CmsVersionRules.sameContent("t1", "s", "c", "t2", "s", "c"));
        assertFalse(CmsVersionRules.sameContent("t", null, "c", "t", "s", "c"));
    }
}
