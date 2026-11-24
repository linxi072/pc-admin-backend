package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 发布态流转规则单测（C1）：规范化、流转判定、幂等、时间合并。 */
class CmsStatusRulesTest {

    @Test
    void shouldNormalizeStatus() {
        assertEquals("DRAFT", CmsStatusRules.normalize(null));
        assertEquals("DRAFT", CmsStatusRules.normalize("  "));
        assertEquals("PUBLISHED", CmsStatusRules.normalize(" published "));
        assertEquals("OFFLINE", CmsStatusRules.normalize("OFFLINE"));
    }

    @Test
    void shouldValidateStatus() {
        assertTrue(CmsStatusRules.isValid("DRAFT"));
        assertTrue(CmsStatusRules.isValid("PUBLISHED"));
        assertTrue(CmsStatusRules.isValid("OFFLINE"));
        assertFalse(CmsStatusRules.isValid("SCHEDULED"));
        assertFalse(CmsStatusRules.isValid(null));
        assertFalse(CmsStatusRules.isValid("draft")); // 未归一前小写非法
    }

    @Test
    void shouldDetectTransition() {
        assertTrue(CmsStatusRules.becamePublished("DRAFT", "PUBLISHED"));
        assertTrue(CmsStatusRules.becamePublished("OFFLINE", "PUBLISHED"));
        assertFalse(CmsStatusRules.becamePublished("PUBLISHED", "PUBLISHED"));
        assertFalse(CmsStatusRules.becamePublished(null, "DRAFT"));
    }

    @Test
    void shouldDetectNoop() {
        assertTrue(CmsStatusRules.isPublishNoop("PUBLISHED"));
        assertFalse(CmsStatusRules.isPublishNoop("DRAFT"));
        assertTrue(CmsStatusRules.isOfflineNoop("OFFLINE"));
        assertFalse(CmsStatusRules.isOfflineNoop("PUBLISHED"));
    }

    @Test
    void shouldResolveTimestamps() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 10, 0);
        LocalDateTime old = LocalDateTime.of(2026, 1, 1, 0, 0);
        assertEquals(now, CmsStatusRules.resolvePublishedAt(true, old, now));
        assertEquals(old, CmsStatusRules.resolvePublishedAt(false, old, now));
        assertEquals(now, CmsStatusRules.resolveOfflineAt(true, null, now));
        assertNull(CmsStatusRules.resolveOfflineAt(false, old, now));
    }

    @Test
    void shouldDecideClearOfflineAt() {
        assertTrue(CmsStatusRules.shouldClearOfflineAt("PUBLISHED"));
        assertTrue(CmsStatusRules.shouldClearOfflineAt("DRAFT"));
        assertFalse(CmsStatusRules.shouldClearOfflineAt("OFFLINE"));
        assertFalse(CmsStatusRules.shouldClearOfflineAt(null));
    }

    @Test
    void shouldDetectPublishable() {
        assertTrue(CmsStatusRules.isPublishable("PUBLISHED"));
        assertFalse(CmsStatusRules.isPublishable("DRAFT"));
        assertFalse(CmsStatusRules.isPublishable("OFFLINE"));
        assertFalse(CmsStatusRules.isPublishable(null));
    }

    @Test
    void shouldNormalizeMixedCaseAndKeepCanonicalStatus() {
        assertEquals("DRAFT", CmsStatusRules.normalize("dRaFt"));
        assertEquals("OFFLINE", CmsStatusRules.normalize("\toffline\n"));
        assertFalse(CmsStatusRules.isValid("   "));
        assertFalse(CmsStatusRules.isValid("PUBLISH"));
    }
}
