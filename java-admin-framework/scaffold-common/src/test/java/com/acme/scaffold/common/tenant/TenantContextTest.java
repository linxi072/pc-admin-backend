package com.acme.scaffold.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 租户上下文单测（TEST-COVERAGE-PLAN 第二批 / T2）。
 * 锁定严格模式（未设置抛异常）、嵌套作用域恢复与跨租户旁路语义，防作用域串台。
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void requireTenantIdShouldFailClosedWhenUnset() {
        assertFalse(TenantContext.isSet());
        assertNull(TenantContext.current());
        assertThrows(IllegalStateException.class, TenantContext::requireTenantId);
        assertFalse(TenantContext.isPlatformAdmin());
    }

    @Test
    void setShouldWriteTenantAndAdminFlag() {
        TenantContext.set(42L, true);
        assertTrue(TenantContext.isSet());
        assertEquals(42L, TenantContext.requireTenantId());
        assertTrue(TenantContext.isPlatformAdmin());
        assertFalse(TenantContext.isBypass());
    }

    @Test
    void setTenantIdShouldNotBePlatformAdmin() {
        TenantContext.setTenantId(7L);
        assertEquals(7L, TenantContext.requireTenantId());
        assertFalse(TenantContext.isPlatformAdmin());
    }

    @Test
    void runWithShouldRestorePreviousScope() {
        TenantContext.set(1L, false);
        TenantContext.runWith(2L, true, () -> {
            assertEquals(2L, TenantContext.requireTenantId());
            assertTrue(TenantContext.isPlatformAdmin());
        });
        assertEquals(1L, TenantContext.requireTenantId(), "外层作用域应恢复");
        assertFalse(TenantContext.isPlatformAdmin());
    }

    @Test
    void runWithShouldClearWhenNoPreviousScope() {
        TenantContext.clear();
        TenantContext.runWith(9L, false, () -> assertEquals(9L, TenantContext.requireTenantId()));
        assertFalse(TenantContext.isSet(), "调用前无作用域，结束后应清除而非残留");
    }

    @Test
    void runWithShouldRestoreEvenIfActionThrows() {
        TenantContext.set(1L, false);
        assertThrows(RuntimeException.class, () -> TenantContext.runWith(2L, true, () -> {
            throw new RuntimeException("boom");
        }));
        assertEquals(1L, TenantContext.requireTenantId(), "异常路径同样恢复作用域");
    }

    @Test
    void runWithBypassShouldRequireExistingScopeAndRestore() {
        assertThrows(IllegalStateException.class, () -> TenantContext.runWithBypass(() -> "x"),
                "无作用域时禁止开启旁路，避免误判");
        TenantContext.set(5L, false);
        String result = TenantContext.runWithBypass(() -> {
            assertTrue(TenantContext.isBypass());
            assertEquals(5L, TenantContext.requireTenantId());
            return "ok";
        });
        assertEquals("ok", result);
        assertFalse(TenantContext.isBypass(), "旁路结束应恢复");
        assertEquals(5L, TenantContext.requireTenantId());
    }

    @Test
    void clearShouldRemoveScope() {
        TenantContext.set(3L, true);
        TenantContext.clear();
        assertFalse(TenantContext.isSet());
        assertThrows(IllegalStateException.class, TenantContext::requireTenantId);
    }

    @Test
    void threadIsolationShouldHold() throws InterruptedException {
        TenantContext.set(100L, false);
        AtomicReference<Long> other = new AtomicReference<>(-1L);
        Thread t = new Thread(() -> {
            assertFalse(TenantContext.isSet(), "子线程不应继承父线程作用域");
            TenantContext.setTenantId(200L);
            other.set(TenantContext.requireTenantId());
        });
        t.start();
        t.join();
        assertEquals(200L, other.get());
        assertEquals(100L, TenantContext.requireTenantId(), "子线程设置不影响父线程");
    }
}
