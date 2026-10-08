package com.acme.scaffold.security.token;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TokenVersionRules} 与 {@link TokenVersionCache} 单元测试。
 *
 * <p>这两处决定「权限变更能否真正作废旧凭证」：判定过松则变更形同虚设（安全问题），
 * 判定过严或缓存不失效则会把正常用户误踢下线（可用性问题）。
 */
class TokenVersionRulesTest {

    // ---------------- 版本判定 ----------------

    @Test
    void claimBehindStoredIsInvalid() {
        assertFalse(TokenVersionRules.isValid(1, 2));
    }

    @Test
    void claimEqualToStoredIsValid() {
        assertTrue(TokenVersionRules.isValid(3, 3));
    }

    @Test
    void claimAheadOfStoredIsValid() {
        // 运维手工回退版本后，旧凭证仍应可用，避免把用户永久锁死
        assertTrue(TokenVersionRules.isValid(5, 3));
    }

    @Test
    void missingUserIsAlwaysInvalid() {
        assertFalse(TokenVersionRules.isValid(1, null));
        assertFalse(TokenVersionRules.isValid(null, null));
    }

    @Test
    void missingClaimFallsBackToDefault() {
        assertEquals(TokenVersionRules.DEFAULT_VERSION, TokenVersionRules.normalizeClaim(null));
        assertEquals(7, TokenVersionRules.normalizeClaim(7));
        // 改造前签发的 token 视为 v1：库中仍是 1 时有效，被 bump 后失效
        assertTrue(TokenVersionRules.isValid(null, 1));
        assertFalse(TokenVersionRules.isValid(null, 2));
    }

    // ---------------- 缓存 ----------------

    @Test
    void cacheExpiresByTtl() {
        TokenVersionCache cache = new TokenVersionCache(1000L);
        cache.put(1L, 4);
        assertEquals(4, cache.get(1L));
        assertTrue(TokenVersionRules.isExpired(System.currentTimeMillis(), 0L, 1000L));
    }

    @Test
    void cacheZeroTtlNeverHits() {
        TokenVersionCache cache = new TokenVersionCache(0L);
        cache.put(1L, 4);
        assertEquals(1, cache.size());
        // TTL=0 时每次读取都已过期，等于关闭缓存（逐请求回查 DB）
        assertFalse(TokenVersionRules.isValid(4, cache.get(1L)));
    }

    @Test
    void invalidateRemovesEntries() {
        TokenVersionCache cache = new TokenVersionCache(30_000L);
        cache.put(1L, 4);
        cache.put(2L, 5);
        cache.invalidate(java.util.List.of(1L));
        assertEquals(null, cache.get(1L));
        assertEquals(5, cache.get(2L));
    }

    @Test
    void nullInputsAreIgnored() {
        TokenVersionCache cache = new TokenVersionCache(30_000L);
        cache.put(null, 1);
        cache.put(1L, null);
        cache.invalidate(null);
        assertEquals(0, cache.size());
        assertEquals(null, cache.get(null));
    }
}
