package com.acme.scaffold.config.center;

import com.acme.scaffold.common.event.DomainEvent;
import com.acme.scaffold.common.event.EventPublisher;
import com.acme.scaffold.common.event.domain.ConfigRefreshedEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfigCenterServiceTest {

    private ConfigCenterService serviceWithSnapshot(Map<String, String> snapshot) {
        ConfigCenterPort port = mock(ConfigCenterPort.class);
        when(port.type()).thenReturn("local");
        when(port.supportsDynamicRefresh()).thenReturn(false);
        when(port.snapshot()).thenReturn(snapshot);
        when(port.refresh()).thenReturn(snapshot.size());
        EventPublisher publisher = mock(EventPublisher.class);
        ConfigCenterProperties props = new ConfigCenterProperties();
        props.setSnapshotPrefix("app");
        return new ConfigCenterService(port, publisher, props);
    }

    @Test
    void refreshPublishesConfigRefreshedEvent() {
        Map<String, String> snap = Map.of("app.a", "1", "app.b", "2");
        ConfigCenterPort port = mock(ConfigCenterPort.class);
        when(port.type()).thenReturn("local");
        when(port.supportsDynamicRefresh()).thenReturn(false);
        when(port.snapshot()).thenReturn(snap);
        when(port.refresh()).thenReturn(snap.size());
        AtomicReference<ConfigRefreshedEvent> captured = new AtomicReference<>();
        EventPublisher publisher = new EventPublisher() {
            @Override
            public void publish(DomainEvent event) {
                if (event instanceof ConfigRefreshedEvent ev) {
                    captured.set(ev);
                }
            }
            @Override
            public void publishAll(List<? extends DomainEvent> events) {
            }
        };
        ConfigCenterProperties props = new ConfigCenterProperties();
        ConfigCenterService service = new ConfigCenterService(port, publisher, props);

        int count = service.refresh();

        assertEquals(2, count);
        assertTrue(captured.get() instanceof ConfigRefreshedEvent);
        assertEquals("local", captured.get().getCenterType());
        assertFalse(captured.get().isRemote());
        assertEquals(2, captured.get().getEffectiveCount());
    }

    @Test
    void snapshotFiltersByConfiguredPrefix() {
        // 配置前缀 app.xxx 仅暴露 app.config-center，过滤 app.cache
        ConfigCenterProperties props = new ConfigCenterProperties();
        props.setSnapshotPrefix("app.config-center");
        ConfigCenterPort port = mock(ConfigCenterPort.class);
        when(port.snapshot()).thenReturn(Map.of(
                "app.config-center.type", "local",
                "app.cache.ttl", "30m",
                "app.jooq.slow-query-threshold-ms", "500"));
        ConfigCenterService service = new ConfigCenterService(port, mock(EventPublisher.class), props);

        Map<String, String> out = service.snapshot(null);
        assertEquals(1, out.size());
        assertTrue(out.containsKey("app.config-center.type"));
    }

    @Test
    void snapshotExplicitPrefixOverridesConfigured() {
        ConfigCenterProperties props = new ConfigCenterProperties();
        props.setSnapshotPrefix("app.config-center");
        ConfigCenterPort port = mock(ConfigCenterPort.class);
        when(port.snapshot()).thenReturn(Map.of(
                "app.cache.ttl", "30m",
                "app.jooq.slow-query-threshold-ms", "500"));
        ConfigCenterService service = new ConfigCenterService(port, mock(EventPublisher.class), props);

        Map<String, String> out = service.snapshot("app.jooq");
        assertEquals(1, out.size());
        assertTrue(out.containsKey("app.jooq.slow-query-threshold-ms"));
    }

    @Test
    void getPropertyDelegatesToPort() {
        ConfigCenterPort port = mock(ConfigCenterPort.class);
        when(port.getProperty("app.x")).thenReturn(Optional.of("v"));
        when(port.type()).thenReturn("local");
        when(port.supportsDynamicRefresh()).thenReturn(false);
        when(port.snapshot()).thenReturn(Map.of());
        ConfigCenterService service = new ConfigCenterService(port, mock(EventPublisher.class), new ConfigCenterProperties());
        assertEquals(Optional.of("v"), service.getProperty("app.x"));
    }

    @Test
    void defaultSnapshotPrefixIsApp() {
        ConfigCenterPort port = mock(ConfigCenterPort.class);
        when(port.snapshot()).thenReturn(Map.of(
                "app.cache.ttl", "30m",
                "app.config-center.type", "local",
                "java.version", "21"));
        ConfigCenterService service = new ConfigCenterService(port, mock(EventPublisher.class), new ConfigCenterProperties());
        Map<String, String> out = service.snapshot(null);
        // 默认前缀 app 应同时保留 app.cache 与 app.config-center，过滤非 app.* 的系统键
        assertEquals(2, out.size());
        assertTrue(out.containsKey("app.cache.ttl"));
        assertTrue(out.containsKey("app.config-center.type"));
        assertFalse(out.containsKey("java.version"));
    }
}
