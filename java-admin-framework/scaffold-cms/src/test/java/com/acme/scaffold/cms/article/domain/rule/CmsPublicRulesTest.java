package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 公开查询规则单测（C7）：分页上限钳制。 */
class CmsPublicRulesTest {

    @Test
    void shouldClampPage() {
        assertEquals(1, CmsPublicRules.clampPage(0));
        assertEquals(1, CmsPublicRules.clampPage(-5));
        assertEquals(3, CmsPublicRules.clampPage(3));
    }

    @Test
    void shouldClampSize() {
        assertEquals(20, CmsPublicRules.clampSize(0));
        assertEquals(20, CmsPublicRules.clampSize(-1));
        assertEquals(10, CmsPublicRules.clampSize(10));
        assertEquals(50, CmsPublicRules.clampSize(50));
        assertEquals(50, CmsPublicRules.clampSize(500)); // 强上限
    }
}
