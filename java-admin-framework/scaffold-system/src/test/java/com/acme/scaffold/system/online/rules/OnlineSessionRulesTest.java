package com.acme.scaffold.system.online.rules;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineSessionRulesTest {

    private static final Duration IDLE_THRESHOLD = Duration.ofMinutes(30);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 12, 0, 0);

    @Test
    void online_whenActiveWithinThreshold() {
        assertEquals("ONLINE",
                OnlineSessionRules.displayStatus(NOW.minusMinutes(29), false, IDLE_THRESHOLD, NOW));
    }

    @Test
    void online_whenActiveExactlyAtThreshold() {
        // 边界值：恰好等于阈值不算空闲（超过才算）
        assertEquals("ONLINE",
                OnlineSessionRules.displayStatus(NOW.minusMinutes(30), false, IDLE_THRESHOLD, NOW));
    }

    @Test
    void idle_whenActiveBeyondThreshold() {
        assertEquals("IDLE",
                OnlineSessionRules.displayStatus(NOW.minusMinutes(31), false, IDLE_THRESHOLD, NOW));
    }

    @Test
    void restricted_takesPriorityOverIdle() {
        // 优先级：RESTRICTED > IDLE > ONLINE——被限制登录的会话即使空闲也标记为已限制
        assertEquals("RESTRICTED",
                OnlineSessionRules.displayStatus(NOW.minusHours(5), true, IDLE_THRESHOLD, NOW));
    }

    @Test
    void restricted_takesPriorityOverOnline() {
        assertEquals("RESTRICTED",
                OnlineSessionRules.displayStatus(NOW, true, IDLE_THRESHOLD, NOW));
    }

    @Test
    void online_whenLastActiveAtIsNull() {
        // last_active_at 为空（异常数据兜底）：按在线处理，不误判为空闲
        assertEquals("ONLINE",
                OnlineSessionRules.displayStatus(null, false, IDLE_THRESHOLD, NOW));
    }

    @Test
    void heartbeatDue_whenPastInterval() {
        assertTrue(OnlineSessionRules.heartbeatDue(61_000L, 0L, 60_000L));
    }

    @Test
    void heartbeatNotDue_whenWithinInterval() {
        assertFalse(OnlineSessionRules.heartbeatDue(59_999L, 0L, 60_000L));
    }

    @Test
    void heartbeatDue_whenExactlyAtInterval() {
        // 边界值：恰好等于间隔视为到点（>=）
        assertTrue(OnlineSessionRules.heartbeatDue(60_000L, 0L, 60_000L));
    }

    @Test
    void heartbeatDue_whenClockSkewedBackwards() {
        // 时钟回拨导致差值为负：不触发写库（保守跳过，多写一次无正确性影响）
        assertFalse(OnlineSessionRules.heartbeatDue(0L, 60_000L, 60_000L));
    }
}
