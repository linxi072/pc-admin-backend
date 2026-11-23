package com.acme.scaffold.common.event.replication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.event.DomainEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 跨区域复制总线单测（E7）：拓扑扇出、幂等去重、零回归默认、统计。 */
class ReplicationBusTest {

    private static final class TestEvent extends DomainEvent {
        TestEvent(Long tenantId) {
            super(tenantId, "test");
        }
    }

    @Test
    void shouldFanOutToOnlinePeersExcludingSource() {
        List<String> delivered = new ArrayList<>();
        ReplicationBus bus = new ReplicationBus();
        bus.configure(new ReplicationTopology(new java.util.LinkedHashMap<>(Map.of(
                "a", true, "b", true, "c", false))));
        bus.register((env, unit) -> delivered.add(unit));

        assertEquals(List.of("b"), bus.replicate(new TestEvent(1L), "a", 1)); // c 离线，仅 b
        assertEquals(List.of("a"), bus.replicate(new TestEvent(2L), "b", 2));
        assertEquals(List.of("b", "a"), delivered);
        assertEquals(2, bus.stats().replicated());
    }

    @Test
    void shouldDeduplicateByEventId() {
        List<String> delivered = new ArrayList<>();
        ReplicationBus bus = new ReplicationBus();
        bus.configure(new ReplicationTopology(Map.of("a", true, "b", true)));
        bus.register((env, unit) -> delivered.add(env.eventId() + "->" + unit));

        TestEvent event = new TestEvent(1L);
        assertEquals(List.of("b"), bus.replicate(event, "a", 1));
        assertTrue(bus.replicate(event, "a", 1).isEmpty()); // 重复 eventId 不重投

        assertEquals(1, delivered.size());
        assertEquals(1, bus.stats().duplicates());
        assertEquals(1, bus.stats().replicated());
    }

    @Test
    void shouldBeNoOpWithoutTargetsOrTopology() {
        ReplicationBus bus = new ReplicationBus();
        TestEvent event = new TestEvent(1L);
        assertTrue(bus.replicate(event, "a", 1).isEmpty());
        assertEquals(0, bus.stats().replicated());
        assertEquals(1, bus.stats().trackedEvents());
    }

    @Test
    void shouldSkipOfflineAndUnknownUnits() {
        ReplicationTopology topology = new ReplicationTopology(Map.of("a", true, "b", false));
        assertTrue(topology.isOnline("a"));
        assertFalse(topology.isOnline("b"));
        assertFalse(topology.isOnline("unknown"));
        assertEquals(List.of("a"), topology.targetsFrom("b"));
        assertTrue(topology.targetsFrom("a").isEmpty());
    }
}
