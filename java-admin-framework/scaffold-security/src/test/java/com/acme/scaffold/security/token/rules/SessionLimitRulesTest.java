package com.acme.scaffold.security.token.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 多端登录限制纯规则单测（TEST-COVERAGE-PLAN 第二批 / T1）。
 * 锁定会话上限判定与「踢最早登录」淘汰契约。
 */
class SessionLimitRulesTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 1, 1, 0, 0);

    @Test
    void evaluateShouldAllowWhenUnlimited() {
        assertEquals(SessionLimitRules.Decision.ALLOW,
                SessionLimitRules.evaluate(100, 0, SessionLimitRules.Strategy.REJECT),
                "maxSessions<=0 表示不限制");
        assertEquals(SessionLimitRules.Decision.ALLOW,
                SessionLimitRules.evaluate(100, -1, SessionLimitRules.Strategy.REJECT));
    }

    @Test
    void evaluateShouldAllowWhenBelowLimit() {
        assertEquals(SessionLimitRules.Decision.ALLOW,
                SessionLimitRules.evaluate(2, 3, SessionLimitRules.Strategy.REJECT));
        assertEquals(SessionLimitRules.Decision.ALLOW,
                SessionLimitRules.evaluate(2, 3, SessionLimitRules.Strategy.EVICT_OLDEST));
    }

    @Test
    void evaluateShouldApplyStrategyWhenAtLimit() {
        assertEquals(SessionLimitRules.Decision.REJECT,
                SessionLimitRules.evaluate(3, 3, SessionLimitRules.Strategy.REJECT));
        assertEquals(SessionLimitRules.Decision.EVICT_OLDEST,
                SessionLimitRules.evaluate(3, 3, SessionLimitRules.Strategy.EVICT_OLDEST));
        assertEquals(SessionLimitRules.Decision.EVICT_OLDEST,
                SessionLimitRules.evaluate(5, 3, SessionLimitRules.Strategy.EVICT_OLDEST),
                "已超限也按淘汰策略处理");
    }

    @Test
    void selectToEvictShouldPickOldestSessions() {
        List<SessionLimitRules.SessionInfo> sessions = List.of(
                new SessionLimitRules.SessionInfo(3L, T0.plusHours(2), T0.plusHours(3)),
                new SessionLimitRules.SessionInfo(1L, T0, T0.plusHours(1)),
                new SessionLimitRules.SessionInfo(2L, T0.plusHours(1), T0.plusHours(2))
        );
        // 保留最新 1 个（id=3），淘汰 id=1,2（按登录时间升序）
        assertEquals(List.of(1L, 2L), SessionLimitRules.selectToEvict(sessions, 1));
    }

    @Test
    void selectToEvictShouldHandleBoundaryInputs() {
        assertEquals(List.of(), SessionLimitRules.selectToEvict(null, 1));
        assertEquals(List.of(), SessionLimitRules.selectToEvict(List.of(), 1));
        List<SessionLimitRules.SessionInfo> two = List.of(
                new SessionLimitRules.SessionInfo(1L, T0, T0),
                new SessionLimitRules.SessionInfo(2L, T0.plusHours(1), T0));
        assertEquals(List.of(), SessionLimitRules.selectToEvict(two, 2));
        assertEquals(List.of(), SessionLimitRules.selectToEvict(two, 5));
        // keepCount<0 收敛为 0 -> 全部淘汰
        assertEquals(List.of(1L, 2L), SessionLimitRules.selectToEvict(two, -3));
    }

    @Test
    void selectToEvictShouldTieBreakByLastUsedAt() {
        List<SessionLimitRules.SessionInfo> sessions = List.of(
                new SessionLimitRules.SessionInfo(2L, T0, T0.plusHours(2)),
                new SessionLimitRules.SessionInfo(1L, T0, T0.plusHours(1))
        );
        // issuedAt 相同，lastUsedAt 更早的 id=1 先被淘汰
        assertEquals(List.of(1L), SessionLimitRules.selectToEvict(sessions, 1));
    }

    @Test
    void selectToEvictShouldNotMutateInput() {
        List<SessionLimitRules.SessionInfo> sessions = new java.util.ArrayList<>(List.of(
                new SessionLimitRules.SessionInfo(2L, T0.plusHours(1), T0),
                new SessionLimitRules.SessionInfo(1L, T0, T0)));
        SessionLimitRules.selectToEvict(sessions, 1);
        assertEquals(2L, sessions.get(0).id(), "原列表顺序不应被排序改变");
    }
}
