package com.acme.scaffold.config.center;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalConfigCenterAdapterTest {

    private ConfigCenterPort newAdapterWith(Map<String, Object> props) {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test-center", props));
        return new LocalConfigCenterAdapter(env);
    }

    @Test
    void typeIsLocalAndDynamicRefreshUnsupported() {
        ConfigCenterPort port = newAdapterWith(Map.of("app.cache.ttl", "30m"));
        assertEquals("local", port.type());
        assertFalse(port.supportsDynamicRefresh());
    }

    @Test
    void readsPropertyFromEnvironment() {
        ConfigCenterPort port = newAdapterWith(Map.of("app.cache.ttl", "30m", "app.jooq.slow-query-threshold-ms", "500"));
        assertEquals(Optional.of("30m"), port.getProperty("app.cache.ttl"));
        assertEquals("500", port.getProperty("app.jooq.slow-query-threshold-ms", "0"));
        assertEquals(Optional.empty(), port.getProperty("app.not.exist"));
    }

    @Test
    void snapshotOnlyExposesAppNamespace() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("app.cache.ttl", "30m");
        props.put("app.config-center.type", "local");
        // 仿系统属性 / 密钥，验证不会被泄露到快照
        props.put("java.version", "21");
        props.put("JWT_SECRET", "super-secret");
        ConfigCenterPort port = newAdapterWith(props);

        Map<String, String> snap = port.snapshot();
        assertTrue(snap.containsKey("app.cache.ttl"));
        assertTrue(snap.containsKey("app.config-center.type"));
        assertFalse(snap.containsKey("java.version"), "系统属性不应进入快照");
        assertFalse(snap.containsKey("JWT_SECRET"), "敏感配置不应进入快照");
    }

    @Test
    void refreshReturnsEffectiveCount() {
        ConfigCenterPort port = newAdapterWith(Map.of("app.a", "1", "app.b", "2"));
        int n = port.refresh();
        assertEquals(2, n);
    }
}
