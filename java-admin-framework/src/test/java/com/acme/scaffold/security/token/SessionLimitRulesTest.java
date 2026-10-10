package com.acme.scaffold.security.token;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionLimitRulesTest {

    @Test
    void unlimited_whenMaxSessionsIsZero() {
        assertEquals(SessionLimitRules.Decision.ALLOW,
                SessionLimitRules.evaluate(100, 0, SessionLimitRules.Strategy.REJECT));
        assertEquals(SessionLimitRules.Decision.ALLOW,
                SessionLimitRules.evaluate(100, 0, SessionLimitRules.Strategy.EVICT_OLDEST));
    }

    @Test
    void allow_whenActiveBelowMax() {
        assertEquals(SessionLimitRules.Decision.ALLOW,
                SessionLimitRules.evaluate(2, 3, SessionLimitRules.Strategy.REJECT));
        assertEquals(SessionLimitRules.Decision.ALLOW,
                SessionLimitRules.evaluate(2, 3, SessionLimitRules.Strategy.EVICT_OLDEST));
    }

    @Test
    void reject_whenAtMax() {
        assertEquals(SessionLimitRules.Decision.REJECT,
                SessionLimitRules.evaluate(3, 3, SessionLimitRules.Strategy.REJECT));
    }

    @Test
    void reject_whenOverMax() {
        assertEquals(SessionLimitRules.Decision.REJECT,
                SessionLimitRules.evaluate(9, 3, SessionLimitRules.Strategy.REJECT));
    }

    @Test
    void evictOldest_whenAtMax() {
        assertEquals(SessionLimitRules.Decision.EVICT_OLDEST,
                SessionLimitRules.evaluate(3, 3, SessionLimitRules.Strategy.EVICT_OLDEST));
    }

    @Test
    void evictOldest_whenOverMax() {
        assertEquals(SessionLimitRules.Decision.EVICT_OLDEST,
                SessionLimitRules.evaluate(9, 3, SessionLimitRules.Strategy.EVICT_OLDEST));
    }

    @Test
    void evictOldest_whenMaxOne() {
        // max=1 且已有 1 个活跃会话：新登录应踢掉最旧会话（保留 0 个旧会话），总计不超过 1
        assertEquals(SessionLimitRules.Decision.EVICT_OLDEST,
                SessionLimitRules.evaluate(1, 1, SessionLimitRules.Strategy.EVICT_OLDEST));
    }
}
