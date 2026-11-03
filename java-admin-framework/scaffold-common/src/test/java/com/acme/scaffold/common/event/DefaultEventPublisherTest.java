package com.acme.scaffold.common.event;

import com.acme.scaffold.common.event.domain.UserDisabledEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultEventPublisher} 行为验证（ROADMAP R2）：分派、类型过滤、错误隔离、批量、空安全。
 * 不依赖 Spring 上下文，手动构造发布器。
 */
class DefaultEventPublisherTest {

    /** 测试用最小事件 A（仅用于验证类型过滤）。 */
    static class EventA extends DomainEvent {
        EventA() { super(null, "test"); }
    }

    /** 测试用最小事件 B。 */
    static class EventB extends DomainEvent {
        EventB() { super(null, "test"); }
    }

    /** 记录收到的事件，用于断言分派正确性。 */
    static class RecordingListener<T extends DomainEvent> implements DomainEventListener<T> {
        final Class<T> type;
        final List<T> received = new ArrayList<>();
        RecordingListener(Class<T> type) { this.type = type; }
        @Override public Class<T> eventType() { return type; }
        @Override public void onEvent(T event) { received.add(event); }
    }

    /** 必抛异常的订阅者，用于验证错误隔离。 */
    static class ThrowingListener extends RecordingListener<EventA> {
        ThrowingListener() { super(EventA.class); }
        @Override public void onEvent(EventA event) { throw new IllegalStateException("boom"); }
    }

    /** 固定返回某桥接实现的 ObjectProvider（避免依赖 Spring 上下文）。 */
    static class FixedBridgeProvider implements ObjectProvider<EventBridge> {
        private final EventBridge bridge;
        FixedBridgeProvider(EventBridge bridge) { this.bridge = bridge; }
        @Override public EventBridge getObject() { return bridge; }
        @Override public EventBridge getObject(Object... args) { return bridge; }
        @Override public EventBridge getIfAvailable() { return bridge; }
        @Override public EventBridge getIfUnique() { return bridge; }
        @Override public java.util.stream.Stream<EventBridge> stream() { return java.util.stream.Stream.of(bridge); }
        @Override public java.util.stream.Stream<EventBridge> orderedStream() { return java.util.stream.Stream.of(bridge); }
    }

    private DefaultEventPublisher publisherWith(EventBridge bridge, DomainEventListener<?>... listeners) {
        return new DefaultEventPublisher(List.of(listeners), new FixedBridgeProvider(bridge));
    }

    @Test
    void publishDeliversToMatchingListener() {
        RecordingListener<EventA> a = new RecordingListener<>(EventA.class);
        DefaultEventPublisher pub = publisherWith(new NoOpEventBridge(), a);

        EventA evt = new EventA();
        pub.publish(evt);

        assertEquals(1, a.received.size());
        assertEquals(evt, a.received.get(0));
        assertNotNull(evt.getEventId());
        assertNotNull(evt.getOccurredAt());
        assertEquals("EventA", evt.eventType());
    }

    @Test
    void publishSkipsNonMatchingListener() {
        RecordingListener<EventA> a = new RecordingListener<>(EventA.class);
        RecordingListener<EventB> b = new RecordingListener<>(EventB.class);
        DefaultEventPublisher pub = publisherWith(new NoOpEventBridge(), a, b);

        pub.publish(new EventA());

        assertEquals(1, a.received.size());
        assertEquals(0, b.received.size());
    }

    @Test
    void listenerExceptionIsIsolated() {
        ThrowingListener bad = new ThrowingListener();
        RecordingListener<EventA> good = new RecordingListener<>(EventA.class);
        DefaultEventPublisher pub = publisherWith(new NoOpEventBridge(), bad, good);

        // 即便 bad 抛异常，发布方法本身不抛，good 仍收到事件
        EventA evt = new EventA();
        pub.publish(evt);

        assertEquals(1, good.received.size());
        assertEquals(evt, good.received.get(0));
    }

    @Test
    void publishNullIsIgnored() {
        RecordingListener<EventA> a = new RecordingListener<>(EventA.class);
        DefaultEventPublisher pub = publisherWith(new NoOpEventBridge(), a);

        pub.publish(null);

        assertTrue(a.received.isEmpty());
    }

    @Test
    void publishAllDispatchesEach() {
        RecordingListener<EventA> a = new RecordingListener<>(EventA.class);
        DefaultEventPublisher pub = publisherWith(new NoOpEventBridge(), a);

        pub.publishAll(Arrays.asList(new EventA(), new EventA(), null));

        assertEquals(2, a.received.size());
    }

    @Test
    void runtimeRegistrationWorks() {
        RecordingListener<EventB> b = new RecordingListener<>(EventB.class);
        DefaultEventPublisher pub = publisherWith(new NoOpEventBridge());
        pub.register(b);

        pub.publish(new EventB());
        assertEquals(1, b.received.size());
    }

    @Test
    void eventBridgeReceivesEveryEvent() {
        final AtomicReference<DomainEvent> bridged = new AtomicReference<>();
        EventBridge recordingBridge = new EventBridge() {
            @Override public void bridge(DomainEvent event) { bridged.set(event); }
        };
        RecordingListener<EventA> a = new RecordingListener<>(EventA.class);
        DefaultEventPublisher pub = publisherWith(recordingBridge, a);

        EventA evt = new EventA();
        pub.publish(evt);

        assertEquals(evt, bridged.get());
    }

    @Test
    void userDisabledEventCarriesContext() {
        UserDisabledEvent evt = new UserDisabledEvent(7L, "system", 42L, "alice", "ACTIVE", "admin",
                List.of("ROLE_USER"));

        assertEquals(42L, evt.getUserId());
        assertEquals("alice", evt.getUsername());
        assertEquals("ACTIVE", evt.getPreviousStatus());
        assertEquals("admin", evt.getOperator());
        assertEquals(List.of("ROLE_USER"), evt.getRevokedRoleCodes());
        assertEquals(7L, evt.getTenantId());
        assertEquals("system", evt.getSource());
        assertEquals("UserDisabledEvent", evt.eventType());
        assertNotNull(evt.getEventId());
    }
}
