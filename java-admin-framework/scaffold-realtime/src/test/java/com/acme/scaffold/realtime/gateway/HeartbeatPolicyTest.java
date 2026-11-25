package com.acme.scaffold.realtime.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 心跳保活策略单测：构造约束与三类到期判定边界。 */
class HeartbeatPolicyTest {

    @Test
    void designDefaultShouldMatchGatewaySpec() {
        HeartbeatPolicy p = HeartbeatPolicy.designDefault();
        assertEquals(30_000L, p.pingIntervalMs());
        assertEquals(90_000L, p.idleTimeoutMs());
        assertEquals(90_000L, p.routeTtlMs());
    }

    @Test
    void constructorShouldRejectInvalidTimingRelations() {
        assertThrows(IllegalArgumentException.class, () -> new HeartbeatPolicy(0, 90_000, 90_000));
        assertThrows(IllegalArgumentException.class, () -> new HeartbeatPolicy(30_000, 30_000, 90_000));
        assertThrows(IllegalArgumentException.class, () -> new HeartbeatPolicy(30_000, 90_000, 89_999));
        assertTrue(new HeartbeatPolicy(30_000, 90_000, 90_000).routeTtlMs() >= 90_000L, "边界值恰好 3 周期应合法");
    }

    @Test
    void pingShouldBeDueAtExactInterval() {
        HeartbeatPolicy p = new HeartbeatPolicy(30_000, 90_000, 90_000);
        assertTrue(p.isPingDue(1_000L, 31_000L));
        assertFalse(p.isPingDue(1_000L, 30_999L));
    }

    @Test
    void idleShouldExpireAtExactTimeout() {
        HeartbeatPolicy p = new HeartbeatPolicy(30_000, 90_000, 90_000);
        assertTrue(p.isIdleExpired(1_000L, 91_000L));
        assertFalse(p.isIdleExpired(1_000L, 90_999L));
    }

    @Test
    void routeShouldExpireOnlyStrictlyBeyondTtl() {
        HeartbeatPolicy p = new HeartbeatPolicy(30_000, 90_000, 90_000);
        assertFalse(p.isRouteExpired(1_000L, 91_000L), "恰好 TTL 仍有效");
        assertTrue(p.isRouteExpired(1_000L, 91_001L));
    }
}
