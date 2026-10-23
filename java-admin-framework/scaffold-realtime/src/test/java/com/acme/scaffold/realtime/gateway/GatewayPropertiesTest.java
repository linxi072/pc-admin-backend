package com.acme.scaffold.realtime.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** {@link GatewayProperties} → {@link GatewaySettings} 映射单测（默认值与覆盖值）。 */
@DisplayName("GatewayProperties 配置映射")
class GatewayPropertiesTest {

    @Test
    @DisplayName("默认值与设计稿一致")
    void defaults_matchDesign() {
        GatewayProperties props = new GatewayProperties();
        GatewaySettings s = props.toGatewaySettings("gw-x");

        assertEquals(200_000, s.maxConnectionsPerNode());
        assertEquals(100.0, s.ratePerConnPerSec());
        assertEquals(500.0, s.burstPerConn());
        assertEquals(SlowConsumerPolicy.DEFAULT_QUEUE_LIMIT, s.slowConsumer().queueLimit());
        assertEquals(0.70, s.slowConsumer().nodeHighWater());
        assertEquals(DrainState.DEFAULT_INFLIGHT_TIMEOUT_MS, s.drainInflightTimeoutMs());
        assertEquals(DrainState.DEFAULT_FLUSH_TIMEOUT_MS, s.drainFlushTimeoutMs());
        assertEquals(4001, s.closeCodeAuthFailed());
        assertEquals(4003, s.closeCodeProtocol());
        assertEquals(4028, s.closeCodeRateLimited());
        assertEquals(4029, s.closeCodeSlowConsumer());
        assertEquals(4030, s.closeCodeIdleTimeout());
        assertEquals(1001, s.closeCodeDraining());
        assertEquals(4027, s.closeCodeCapacity());
        assertEquals(4009, s.closeCodeSessionReplaced());
    }

    @Test
    @DisplayName("覆盖值生效（关闭码与容量）")
    void overrides_applied() {
        GatewayProperties props = new GatewayProperties();
        props.setMaxConnectionsPerNode(10);
        props.setCloseCodeAuthFailed(4100);
        props.setCloseCodeSessionReplaced(4010);
        props.getSlowConsumer().setQueueLimit(50);

        GatewaySettings s = props.toGatewaySettings("gw-y");
        assertEquals(10, s.maxConnectionsPerNode());
        assertEquals(4100, s.closeCodeAuthFailed());
        assertEquals(4010, s.closeCodeSessionReplaced());
        assertEquals(50, s.slowConsumer().queueLimit());
    }

    @Test
    @DisplayName("心跳参数映射到 HeartbeatPolicy")
    void heartbeat_mapping() {
        GatewayProperties props = new GatewayProperties();
        props.getHeartbeat().setPingIntervalMs(15_000L);
        props.getHeartbeat().setIdleTimeoutMs(45_000L);
        props.getHeartbeat().setRouteTtlMs(45_000L);

        GatewaySettings s = props.toGatewaySettings("gw-z");
        assertEquals(15_000L, s.heartbeat().pingIntervalMs());
        assertEquals(45_000L, s.heartbeat().idleTimeoutMs());
        assertEquals(45_000L, s.heartbeat().routeTtlMs());
    }
}
