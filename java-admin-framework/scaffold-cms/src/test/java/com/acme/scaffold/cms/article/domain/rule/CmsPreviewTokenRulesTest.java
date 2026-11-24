package com.acme.scaffold.cms.article.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 预览令牌规则单测（C6）：令牌格式、默认有效期、过期判定。 */
class CmsPreviewTokenRulesTest {

    @Test
    void shouldGenerateValidToken() {
        String token = CmsPreviewTokenRules.generateToken();
        assertEquals(32, token.length());
        assertTrue(CmsPreviewTokenRules.validFormat(token));
        assertTrue(token.matches("[0-9a-f]{32}"));
        assertFalse(token.contains("-"));
    }

    @Test
    void shouldValidateFormat() {
        assertTrue(CmsPreviewTokenRules.validFormat("a".repeat(32)));
        assertFalse(CmsPreviewTokenRules.validFormat(null));
        assertFalse(CmsPreviewTokenRules.validFormat(""));
        assertFalse(CmsPreviewTokenRules.validFormat("a".repeat(31)));
        assertFalse(CmsPreviewTokenRules.validFormat("A".repeat(32))); // 仅小写
        assertFalse(CmsPreviewTokenRules.validFormat("g" + "a".repeat(31)));
        assertFalse(CmsPreviewTokenRules.validFormat("01234567-89ab-cdef-0123-456789abcdef"));
    }

    @Test
    void shouldComputeExpiry() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 10, 0);
        assertEquals(now.plusHours(24), CmsPreviewTokenRules.expiryAfter(now, 24));
        assertEquals(now.plusHours(24), CmsPreviewTokenRules.expiryAfter(now, 0)); // 非法 TTL 回退默认
        assertEquals(now.plusHours(24), CmsPreviewTokenRules.expiryAfter(now, -1));
        assertEquals(now.plusHours(2), CmsPreviewTokenRules.expiryAfter(now, 2));
    }

    @Test
    void shouldDetectExpiry() {
        LocalDateTime now = LocalDateTime.of(2026, 11, 24, 10, 0);
        assertTrue(CmsPreviewTokenRules.isExpired(now.minusMinutes(1), now));
        assertFalse(CmsPreviewTokenRules.isExpired(now, now)); // 等于视为未过期
        assertFalse(CmsPreviewTokenRules.isExpired(now.plusMinutes(1), now));
        assertFalse(CmsPreviewTokenRules.isExpired(null, now));
    }
}
