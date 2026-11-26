package com.acme.scaffold.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 租户强制隔离裁决单测：无作用域时按默认租户收紧，不能静默放行全表访问。 */
class TenantEnforcementTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        TenantEnforcement.configure(null);
    }

    @Test
    void disabledEnforcementShouldNotFilterAndWritePlatformTenant() {
        TenantEnforcement.configure(props(false));

        assertFalse(TenantEnforcement.shouldEnforce("sys_user"));
        assertThrows(IllegalStateException.class, TenantEnforcement::currentTenantId);
    }

    @Test
    void enabledWithoutScopeShouldFallBackToDefaultTenant() {
        TenantEnforcement.configure(props(true));

        assertTrue(TenantEnforcement.shouldEnforce("sys_user"));
        assertEquals(7L, TenantEnforcement.currentTenantId());
    }

    @Test
    void explicitScopeShouldOverrideDefaultTenant() {
        TenantEnforcement.configure(props(true));
        TenantContext.setTenantId(42L);

        assertTrue(TenantEnforcement.shouldEnforce("sys_user"));
        assertEquals(42L, TenantEnforcement.currentTenantId());
    }

    @Test
    void platformTableShouldStayExemptWithoutScope() {
        TenantEnforcement.configure(props(true));

        assertFalse(TenantEnforcement.shouldEnforce("sys_menu"));
    }

    @Test
    void bypassScopeShouldStayExempt() {
        TenantEnforcement.configure(props(true));
        TenantContext.TenantScope bypass = new TenantContext.TenantScope(42L, true, true);

        TenantContext.runWith(bypass, () -> assertFalse(TenantEnforcement.shouldEnforce("sys_user")));
    }

    private static TenantProperties props(boolean enforce) {
        TenantProperties props = new TenantProperties();
        props.setEnabled(true);
        props.setEnforce(enforce);
        props.setDefaultTenantId(7L);
        props.setPlatformTables(List.of("sys_menu", "sys_dict_data"));
        return props;
    }
}
