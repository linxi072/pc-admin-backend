package com.acme.scaffold.admin.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** 数据源时区守卫单测：URL 参数解析、偏移换算与启动期 fail-fast 边界。 */
class DataSourceTimeZoneGuardTest {

    private TimeZone originalZone;

    @BeforeEach
    void useUtcJvm() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach
    void restoreJvmZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void declaredZoneShouldReadBothConnectorAliasesCaseInsensitively() {
        assertEquals("UTC", DataSourceTimeZoneGuard.declaredZone(
                "jdbc:mysql://host/db?serverTimezone=UTC"));
        assertEquals("Asia/Shanghai", DataSourceTimeZoneGuard.declaredZone(
                "jdbc:mysql://host/db?useSSL=false&CONNECTIONTIMEZONE=Asia/Shanghai&x=1"));
        assertNull(DataSourceTimeZoneGuard.declaredZone("jdbc:mysql://host/db?useSSL=true"));
        assertNull(DataSourceTimeZoneGuard.declaredZone(null));
    }

    @Test
    void declaredOffsetShouldResolveAliasesAndRejectUnknown() {
        assertEquals(0, DataSourceTimeZoneGuard.declaredOffsetMillis("UTC"));
        assertEquals(0, DataSourceTimeZoneGuard.declaredOffsetMillis("etc/UTC"));
        assertEquals(8 * 3600_000, DataSourceTimeZoneGuard.declaredOffsetMillis("Asia/Shanghai"));
        assertEquals(-5 * 3600_000, DataSourceTimeZoneGuard.declaredOffsetMillis("America/New_York"));
        assertNull(DataSourceTimeZoneGuard.declaredOffsetMillis("SERVER"));
        assertNull(DataSourceTimeZoneGuard.declaredOffsetMillis("not/a/zone"));
        assertNull(DataSourceTimeZoneGuard.declaredOffsetMillis(null));
        assertNull(DataSourceTimeZoneGuard.declaredOffsetMillis("  "));
    }

    @Test
    void verifyShouldPassWhenJdbcAndJvmBothUtc() {
        MockEnvironment env = environment("jdbc:mysql://host/db?serverTimezone=UTC", true);
        assertDoesNotThrow(() -> new DataSourceTimeZoneGuard(env).verify());
    }

    @Test
    void verifyShouldFailFastOnOffsetMismatch() {
        MockEnvironment env = environment("jdbc:mysql://host/db?serverTimezone=Asia/Shanghai", true);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class, () -> new DataSourceTimeZoneGuard(env).verify());
        assertTrue(ex.getMessage().contains("时区不一致"));
        assertTrue(ex.getMessage().contains(DataSourceTimeZoneGuard.ENABLED_PROPERTY));
    }

    @Test
    void verifyShouldWarnOnlyWhenDisabledOrUnresolvable() {
        MockEnvironment disabled = environment("jdbc:mysql://host/db?serverTimezone=Asia/Shanghai", false);
        assertDoesNotThrow(() -> new DataSourceTimeZoneGuard(disabled).verify());

        MockEnvironment serverSemantics = environment("jdbc:mysql://host/db?serverTimezone=SERVER", true);
        assertDoesNotThrow(() -> new DataSourceTimeZoneGuard(serverSemantics).verify());

        MockEnvironment noUrl = new MockEnvironment();
        noUrl.setProperty(DataSourceTimeZoneGuard.ENABLED_PROPERTY, "true");
        assertDoesNotThrow(() -> new DataSourceTimeZoneGuard(noUrl).verify());
    }

    @Test
    void verifyShouldFallBackToLegacySpringDatasourceUrl() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(DataSourceTimeZoneGuard.ENABLED_PROPERTY, "true");
        env.setProperty("spring.datasource.url", "jdbc:mysql://host/db?serverTimezone=UTC");
        assertDoesNotThrow(() -> new DataSourceTimeZoneGuard(env).verify());
    }

    private static MockEnvironment environment(String url, boolean enabled) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty(DataSourceTimeZoneGuard.ENABLED_PROPERTY, String.valueOf(enabled));
        env.setProperty(DataSourceTimeZoneGuard.PRIMARY_URL_PROPERTY, url);
        return env;
    }
}
