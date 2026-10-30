package com.acme.scaffold.realtime.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link ConnectionRegistry} 路由表单测：注册 / 寻路 / 重连驱逐 / TTL 清理。 */
@DisplayName("ConnectionRegistry 路由表")
class ConnectionRegistryTest {

    private final ConnectionRegistry registry = new ConnectionRegistry();
    private final MutableClock clock = new MutableClock(0L);

    @Test
    @DisplayName("注册后可按用户与连接寻路")
    void registerAndFind() {
        registry.register("c1", "u1", "gw-1", clock.nowMillis());
        Optional<RouteEntry> found = registry.findConn("u1");
        assertTrue(found.isPresent());
        assertEquals("c1", found.get().connId());
        assertEquals(1, registry.size());
    }

    @Test
    @DisplayName("同用户重连：旧连接被驱逐并返回其 connId")
    void reconnect_evictsOld() {
        registry.register("c1", "u1", "gw-1", clock.nowMillis());
        String evicted = registry.register("c2", "u1", "gw-1", clock.nowMillis());
        assertEquals("c1", evicted);
        // 指向最新连接
        assertEquals("c2", registry.findConn("u1").orElseThrow().connId());
        // 旧连接已从连接维度移除
        assertFalse(registry.entry("c1").isPresent());
    }

    @Test
    @DisplayName("remove 仅在 userId 仍指向该连接时解除索引，保护新连接")
    void remove_protectsNewConn() {
        registry.register("c1", "u1", "gw-1", clock.nowMillis());
        registry.register("c2", "u1", "gw-1", clock.nowMillis());
        registry.remove("c1"); // 旧连接，已被覆盖
        assertTrue(registry.findConn("u1").isPresent());
        assertEquals("c2", registry.findConn("u1").orElseThrow().connId());
    }

    @Test
    @DisplayName("TTL 过期清理：超过 routeTtlMs 的连接被回收")
    void expireIdle_removesStale() {
        registry.register("c1", "u1", "gw-1", 0L);
        registry.register("c2", "u2", "gw-1", 0L);
        // 仅 c1 刷新到最后时刻，c2 停留在 0
        registry.touch("c1", 90_000L);
        List<String> expired = registry.expireIdle(90_000L, 90_001L);
        assertEquals(List.of("c2"), expired);
        assertFalse(registry.findConn("u2").isPresent());
        assertTrue(registry.findConn("u1").isPresent());
    }

    @Test
    @DisplayName("clear 清空全部索引")
    void clear_empties() {
        registry.register("c1", "u1", "gw-1", clock.nowMillis());
        registry.clear();
        assertEquals(0, registry.size());
        assertEquals(0, registry.userCount());
    }
}
