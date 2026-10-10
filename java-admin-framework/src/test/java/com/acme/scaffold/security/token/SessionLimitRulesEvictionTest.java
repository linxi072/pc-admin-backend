package com.acme.scaffold.security.token;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionLimitRulesEvictionTest {

    private SessionLimitRules.SessionInfo s(long id, LocalDateTime issuedAt, LocalDateTime used) {
        return new SessionLimitRules.SessionInfo(id, issuedAt, used);
    }

    @Test
    void evictsEarliestLoginWhenOverLimit() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<SessionLimitRules.SessionInfo> sessions = List.of(
                s(1L, t.plusMinutes(10), t.plusMinutes(15)), // 最早登录
                s(2L, t.plusMinutes(20), t.plusMinutes(21)),
                s(3L, t.plusMinutes(30), t.plusMinutes(31)),
                s(4L, t.plusMinutes(40), t.plusMinutes(41))  // 最晚登录
        );
        // keepCount = maxSessions - 1 = 3：保留登录时间最新的 3 个，淘汰最早登录的 id=1
        assertEquals(List.of(1L), SessionLimitRules.selectToEvict(sessions, 3));
    }

    @Test
    void keepsAllWhenWithinLimit() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<SessionLimitRules.SessionInfo> sessions = List.of(
                s(1L, t.plusMinutes(10), null),
                s(2L, t.plusMinutes(20), null)
        );
        assertTrue(SessionLimitRules.selectToEvict(sessions, 3).isEmpty());
    }

    @Test
    void negativeKeepCountTreatedAsZero() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0);
        List<SessionLimitRules.SessionInfo> sessions = List.of(
                s(1L, t.plusMinutes(10), null),
                s(2L, t.plusMinutes(20), null)
        );
        // keepCount=0 → 全部淘汰（最早登录先被淘汰）
        assertEquals(List.of(1L, 2L), SessionLimitRules.selectToEvict(sessions, 0));
    }

    @Test
    void tieOnIssuedAtBrokenByLastUsed() {
        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0);
        // 两条会话登录时间相同，最近使用的不同：更早「未使用」者先被淘汰
        List<SessionLimitRules.SessionInfo> sessions = List.of(
                s(1L, t, t.plusMinutes(5)),  // 较早上使用
                s(2L, t, t.plusMinutes(10))  // 较晚上使用
        );
        assertEquals(List.of(1L), SessionLimitRules.selectToEvict(sessions, 1));
    }
}
