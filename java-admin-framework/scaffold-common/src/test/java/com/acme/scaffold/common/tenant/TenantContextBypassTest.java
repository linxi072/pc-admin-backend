package com.acme.scaffold.common.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantContext} 跨租户旁路（bypass）能力测试。
 */
class TenantContextBypassTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("runWithBypass 在作用域内置位 bypass 并在结束后恢复")
    void runWithBypassSetsFlagAndRestores() {
        TenantContext.setTenantId(5);
        assertFalse(TenantContext.isBypass());

        String inside = TenantContext.runWithBypass(() -> {
            assertTrue(TenantContext.isBypass(), "作用域内应处于旁路");
            assertTrue(TenantContext.isSet());
            assertEquals(5L, TenantContext.requireTenantId());
            return "ok";
        });

        assertEquals("ok", inside);
        assertFalse(TenantContext.isBypass(), "作用域外应恢复非旁路");
        assertEquals(5L, TenantContext.requireTenantId());
    }

    @Test
    @DisplayName("runWithBypass 未设置上下文时抛异常（防止隐式越权）")
    void runWithBypassRequiresContext() {
        TenantContext.clear();
        assertThrows(IllegalStateException.class, () -> TenantContext.runWithBypass(() -> "x"));
    }
}
