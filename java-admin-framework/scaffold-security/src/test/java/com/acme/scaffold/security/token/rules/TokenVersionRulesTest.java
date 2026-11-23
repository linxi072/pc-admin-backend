package com.acme.scaffold.security.token.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Token 版本纯规则单测（TEST-COVERAGE-PLAN 第二批 / T1 安全核心）。
 * 锁定「版本失效判定」契约，防止改动 claim/stored 比较语义导致令牌提前失效或该失效未失效。
 */
class TokenVersionRulesTest {

    @Test
    void normalizeClaimShouldDefaultHistoricalTokensToOne() {
        assertEquals(TokenVersionRules.DEFAULT_VERSION, TokenVersionRules.normalizeClaim(null));
        assertEquals(1, TokenVersionRules.DEFAULT_VERSION);
        assertEquals(3, TokenVersionRules.normalizeClaim(3));
    }

    @Test
    void isValidShouldRejectWhenStoredVersionMissing() {
        // 库里版本为 null 表示用户已删除 -> 一律失效
        assertFalse(TokenVersionRules.isValid(null, null));
        assertFalse(TokenVersionRules.isValid(1, null));
    }

    @Test
    void isValidShouldUseGreaterOrEqualSemantics() {
        assertTrue(TokenVersionRules.isValid(2, 2), "版本相等仍有效");
        assertTrue(TokenVersionRules.isValid(3, 2), "claim 超前（人为上调）仍有效，避免永久锁死");
        assertFalse(TokenVersionRules.isValid(1, 2), "库中版本超前 -> 旧令牌失效");
    }

    @Test
    void isValidShouldTreatMissingClaimAsDefaultVersion() {
        assertFalse(TokenVersionRules.isValid(null, 2), "历史 token 无声明按 1，库中已到 2 -> 失效");
        assertTrue(TokenVersionRules.isValid(null, 1), "历史 token 无声明按 1，库中仍为 1 -> 有效");
    }

    @Test
    void isExpiredShouldUseInclusiveThreshold() {
        // 实现为 now - loadedAt >= ttl：恰好等于 TTL 即视为过期
        assertTrue(TokenVersionRules.isExpired(1000L, 0L, 1000L), "达到 TTL 即过期");
        assertTrue(TokenVersionRules.isExpired(1001L, 0L, 1000L));
        assertFalse(TokenVersionRules.isExpired(500L, 0L, 1000L));
    }
}
