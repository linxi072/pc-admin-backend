package com.acme.scaffold.config.center;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.event.DomainEvent;
import com.acme.scaffold.common.event.EventPublisher;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 配置中心快照安全回归：前缀白名单不能成为密钥回显的旁路。 */
class ConfigCenterSnapshotSafetyTest {

    private static final String JWT_SECRET = "jwt-plain-secret-123";
    private static final String PRIVATE_KEY = "private-key-plain-456";
    private static final String API_KEY = "api-key-plain-789";
    private static final String DB_PASSWORD = "db-password-plain";

    @Test
    void localAdapterShouldMaskSecurityNamespaceSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.feature.enabled", "true");
        env.setProperty("app.security.jwt-secret", JWT_SECRET);
        env.setProperty("app.security.oauth2.private-key", PRIVATE_KEY);
        env.setProperty("spring.datasource.password", "system-password-plain");

        Map<String, String> snapshot = new LocalConfigCenterAdapter(env).snapshot();

        assertEquals("true", snapshot.get("app.feature.enabled"));
        assertEquals(ConfigSnapshotSanitizer.MASKED_VALUE, snapshot.get("app.security.jwt-secret"));
        assertEquals(ConfigSnapshotSanitizer.MASKED_VALUE,
                snapshot.get("app.security.oauth2.private-key"));
        assertFalse(snapshot.containsKey("spring.datasource.password"));
        assertFalse(containsValue(snapshot, JWT_SECRET, PRIVATE_KEY, "system-password-plain"));
    }

    @Test
    void serviceShouldMaskSensitiveKeysUnderDefaultPrefix() {
        ConfigCenterService service = new ConfigCenterService(
                new FakePort(sensitiveValues()), NOOP_EVENT_PUBLISHER, new ConfigCenterProperties());

        Map<String, String> snapshot = service.snapshot(null);

        assertEquals(6, snapshot.size());
        assertEquals("true", snapshot.get("app.feature.enabled"));
        assertEquals("Demo", snapshot.get("app.service.display-name"));
        assertEquals(ConfigSnapshotSanitizer.MASKED_VALUE, snapshot.get("app.security.jwt-secret"));
        assertEquals(ConfigSnapshotSanitizer.MASKED_VALUE,
                snapshot.get("app.security.oauth2.private-key"));
        assertEquals(ConfigSnapshotSanitizer.MASKED_VALUE, snapshot.get("app.service.apiKey"));
        assertEquals(ConfigSnapshotSanitizer.MASKED_VALUE, snapshot.get("app.db.password"));
        assertFalse(containsValue(snapshot, JWT_SECRET, PRIVATE_KEY, API_KEY, DB_PASSWORD));
    }

    @Test
    void emptyPrefixWhitelistShouldNotBypassMasking() {
        ConfigCenterProperties properties = new ConfigCenterProperties();
        properties.setSnapshotPrefix("");
        Map<String, String> values = sensitiveValues();
        values.put("other.system.secret", "outside-secret-plain");
        ConfigCenterService service = new ConfigCenterService(
                new FakePort(values), NOOP_EVENT_PUBLISHER, properties);

        Map<String, String> snapshot = service.snapshot(null);

        assertEquals(7, snapshot.size());
        assertEquals(ConfigSnapshotSanitizer.MASKED_VALUE, snapshot.get("other.system.secret"));
        assertFalse(containsValue(snapshot, JWT_SECRET, PRIVATE_KEY, API_KEY, DB_PASSWORD,
                "outside-secret-plain"));
    }

    @Test
    void explicitSecurityPrefixShouldNotUnlockSecrets() {
        ConfigCenterService service = new ConfigCenterService(
                new FakePort(sensitiveValues()), NOOP_EVENT_PUBLISHER, new ConfigCenterProperties());

        Map<String, String> secrets = service.snapshot("app.security");
        Map<String, String> features = service.snapshot("app.feature");

        assertEquals(Map.of(
                "app.security.jwt-secret", ConfigSnapshotSanitizer.MASKED_VALUE,
                "app.security.oauth2.private-key", ConfigSnapshotSanitizer.MASKED_VALUE), secrets);
        assertEquals(Map.of("app.feature.enabled", "true"), features);
    }

    @Test
    void statusCountAndRefreshShouldStayConsistentWithMaskedSnapshot() {
        FakePort port = new FakePort(sensitiveValues());
        ConfigCenterService service = new ConfigCenterService(
                port, NOOP_EVENT_PUBLISHER, new ConfigCenterProperties());

        assertEquals(6, service.snapshotSize());
        assertEquals(6, service.refresh());
        assertEquals(1, port.refreshCount());
        assertFalse(containsValue(service.snapshot(null), JWT_SECRET, PRIVATE_KEY, API_KEY, DB_PASSWORD));
    }

    @Test
    void sensitiveKeyDetectionShouldCoverCommonCredentialPatterns() {
        List<String> sensitive = List.of(
                "APP.SECURITY.JWT-SECRET",
                "app.service.password",
                "app.service.passwd",
                "app.service.pwd",
                "app.service.private-key",
                "app.service.privateKey",
                "app.service.apiKey",
                "app.service.access-key",
                "app.service.credentials",
                "app.service.keystore.password",
                "app.service.authorization",
                "app.service.bearer-token");
        List<String> safe = List.of(
                "app.feature.enabled",
                "app.service.display-name",
                "app.security-scope.enabled");

        assertTrue(sensitive.stream().allMatch(ConfigSnapshotSanitizer::isSensitiveKey));
        assertTrue(safe.stream().noneMatch(ConfigSnapshotSanitizer::isSensitiveKey));
    }

    private static Map<String, String> sensitiveValues() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("app.feature.enabled", "true");
        values.put("app.service.display-name", "Demo");
        values.put("app.security.jwt-secret", JWT_SECRET);
        values.put("app.security.oauth2.private-key", PRIVATE_KEY);
        values.put("app.service.apiKey", API_KEY);
        values.put("app.db.password", DB_PASSWORD);
        return values;
    }

    private static boolean containsValue(Map<String, String> map, String... candidates) {
        return java.util.Arrays.stream(candidates).anyMatch(map::containsValue);
    }

    private static final EventPublisher NOOP_EVENT_PUBLISHER = new EventPublisher() {
        @Override
        public void publish(DomainEvent event) {
        }

        @Override
        public void publishAll(List<? extends DomainEvent> events) {
        }
    };

    private static final class FakePort implements ConfigCenterPort {

        private final Map<String, String> values;
        private int refreshCount;

        private FakePort(Map<String, String> values) {
            this.values = values;
        }

        @Override
        public String type() {
            return "fake";
        }

        @Override
        public boolean supportsDynamicRefresh() {
            return false;
        }

        @Override
        public Optional<String> getProperty(String key) {
            return Optional.ofNullable(values.get(key));
        }

        @Override
        public Map<String, String> snapshot() {
            return new LinkedHashMap<>(values);
        }

        @Override
        public int refresh() {
            refreshCount++;
            return values.size();
        }

        private int refreshCount() {
            return refreshCount;
        }
    }
}
