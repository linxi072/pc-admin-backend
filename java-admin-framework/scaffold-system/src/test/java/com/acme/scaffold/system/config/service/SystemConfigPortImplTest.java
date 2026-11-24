package com.acme.scaffold.system.config.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.config.SystemConfigs;
import com.acme.scaffold.system.config.domain.ConfigView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 系统变量端口单测（配置归口）：优先系统变量、回退默认值、停用/空值/非法值处理。 */
class SystemConfigPortImplTest {

    @AfterEach
    void tearDown() {
        SystemConfigs.unbind();
    }

    private ConfigView vo(String key, String value, String def, String status) {
        return new ConfigView(1L, key, "cfg", value, def, "STRING", null, 1, status);
    }

    @Test
    void shouldPreferSystemConfigOverDefault() {
        ConfigService service = mock(ConfigService.class);
        when(service.getByKey("app.file.max-bytes")).thenReturn(vo("app.file.max-bytes", "2048", "1024", "ACTIVE"));
        SystemConfigs.bind(new SystemConfigPortImpl(service));
        assertEquals(2048L, SystemConfigs.getLong("app.file.max-bytes", 1024L));
        assertEquals("2048", SystemConfigs.getString("app.file.max-bytes", "x"));
    }

    @Test
    void shouldFallbackToDefaultWhenMissingOrDisabled() {
        ConfigService service = mock(ConfigService.class);
        when(service.getByKey("k1")).thenReturn(null);
        when(service.getByKey("k2")).thenReturn(vo("k2", "on", "off", "DISABLED"));
        when(service.getByKey("k3")).thenReturn(vo("k3", "", "fallback", "ACTIVE"));
        SystemConfigs.bind(new SystemConfigPortImpl(service));
        assertEquals("d", SystemConfigs.getString("k1", "d"));
        assertFalse(SystemConfigs.getBoolean("k2", false));
        assertEquals("fallback", SystemConfigs.getString("k3", "other"));
    }

    @Test
    void shouldFallbackOnInvalidTypedValue() {
        ConfigService service = mock(ConfigService.class);
        when(service.getByKey("num")).thenReturn(vo("num", "abc", "9", "ACTIVE"));
        SystemConfigs.bind(new SystemConfigPortImpl(service));
        assertEquals(9, SystemConfigs.getInt("num", 9));
        assertEquals(9L, SystemConfigs.getLong("num", 9L));
    }

    @Test
    void shouldParseBooleanStrictly() {
        ConfigService service = mock(ConfigService.class);
        when(service.getByKey("b1")).thenReturn(vo("b1", "TRUE", "false", "ACTIVE"));
        when(service.getByKey("b2")).thenReturn(vo("b2", "yes", "false", "ACTIVE"));
        SystemConfigs.bind(new SystemConfigPortImpl(service));
        assertTrue(SystemConfigs.getBoolean("b1", false));
        assertTrue(SystemConfigs.getBoolean("b2", true));  // 非法值回退到调用方默认值
        assertFalse(SystemConfigs.getBoolean("b2", false));
    }

    @Test
    void shouldWorkWithoutBinding() {
        SystemConfigs.unbind();
        assertEquals("d", SystemConfigs.getString("any", "d"));
        assertEquals(7, SystemConfigs.getInt("any", 7));
        assertTrue(SystemConfigs.getBoolean("any", true));
    }
}
