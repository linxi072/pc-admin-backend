package com.acme.scaffold.realtime.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link HeartbeatPolicy} 心跳策略单测。 */
@DisplayName("HeartbeatPolicy 心跳与保活")
class HeartbeatPolicyTest {

    private final HeartbeatPolicy policy = HeartbeatPolicy.designDefault(); // 30s/90s/90s

    @Test
    @DisplayName("ping 周期到达判据")
    void pingDue() {
        assertFalse(policy.isPingDue(100_000L, 120_000L)); // 差 20s < 30s
        assertTrue(policy.isPingDue(100_000L, 130_000L));  // 差 30s >= 30s
    }

    @Test
    @DisplayName("读写空闲超时判据")
    void idleExpired() {
        assertFalse(policy.isIdleExpired(100_000L, 189_000L)); // 差 89s < 90s
        assertTrue(policy.isIdleExpired(100_000L, 191_000L));  // 差 91s >= 90s
    }

    @Test
    @DisplayName("路由 TTL 过期判据（严格大于）")
    void routeExpired() {
        assertFalse(policy.isRouteExpired(100_000L, 190_000L)); // 差 90s，不 > 90s
        assertTrue(policy.isRouteExpired(100_000L, 190_001L));  // 差 90.001s > 90s
    }
}
