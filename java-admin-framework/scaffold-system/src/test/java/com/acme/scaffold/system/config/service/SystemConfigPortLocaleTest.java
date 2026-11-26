package com.acme.scaffold.system.config.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 系统变量启用判定 Locale 隔离：inactive 在 tr-TR 下仍视为停用。 */
class SystemConfigPortLocaleTest {

    private Locale originalLocale;

    @BeforeEach
    void rememberLocale() {
        originalLocale = Locale.getDefault();
    }

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    @Test
    void inactiveShouldBeRejectedUnderTurkishLocale() throws Exception {
        Locale.setDefault(new Locale("tr", "TR"));
        Method isActive = SystemConfigPortImpl.class.getDeclaredMethod("isActive", String.class);
        isActive.setAccessible(true);

        assertFalse((Boolean) isActive.invoke(null, "inactive"));
        assertFalse((Boolean) isActive.invoke(null, "disabled"));
        assertTrue((Boolean) isActive.invoke(null, "active"));
    }
}
