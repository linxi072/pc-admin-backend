package com.acme.scaffold.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantContext} 离线单测：ThreadLocal 作用域的读写、严格模式（未设置抛异常）、
 * 平台管理员标记与嵌套 runWith 的作用域保存/恢复。
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("set/isSet/current：写入后可读取，clear 后清除")
    void setAndClear() {
        assertFalse(TenantContext.isSet());
        TenantContext.set(7L, false);
        assertTrue(TenantContext.isSet());
        assertEquals(7L, TenantContext.current().tenantId());
        assertFalse(TenantContext.current().platformAdmin());

        TenantContext.clear();
        assertFalse(TenantContext.isSet());
    }

    @Test
    @DisplayName("requireTenantId：未设置抛出 IllegalStateException")
    void requireTenantId_throwsWhenUnset() {
        assertThrows(IllegalStateException.class, TenantContext::requireTenantId);
    }

    @Test
    @DisplayName("requireTenantId：设置后返回租户 ID")
    void requireTenantId_returnsWhenSet() {
        TenantContext.set(12L, false);
        assertEquals(12L, TenantContext.requireTenantId());
    }

    @Test
    @DisplayName("isPlatformAdmin：反映显式标记")
    void isPlatformAdmin() {
        assertFalse(TenantContext.isPlatformAdmin());
        TenantContext.set(0L, true);
        assertTrue(TenantContext.isPlatformAdmin());
        TenantContext.setTenantId(5L);
        assertFalse(TenantContext.isPlatformAdmin());
    }

    @Test
    @DisplayName("runWith：执行后恢复调用前作用域；调用前未设置则清除")
    void runWith_restoresPrevious() {
        TenantContext.set(1L, false);
        TenantContext.runWith(99L, true, () -> {
            assertEquals(99L, TenantContext.requireTenantId());
            assertTrue(TenantContext.isPlatformAdmin());
        });
        // 外层作用域恢复
        assertEquals(1L, TenantContext.requireTenantId());
        assertFalse(TenantContext.isPlatformAdmin());

        TenantContext.clear();
        TenantContext.runWith(42L, false, () -> assertEquals(42L, TenantContext.requireTenantId()));
        // 调用前未设置 → 执行后清除
        assertFalse(TenantContext.isSet());
    }
}
