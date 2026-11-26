package com.acme.scaffold.system.org.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.security.permission.DataScopeContext;
import com.acme.scaffold.security.permission.DataScopeResult;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 机构树缓存键租户维度回归：强隔离下不同租户即使同为 ALL 也不能命中同一缓存。 */
class OrgCacheKeyTenantTest {

    private final OrgService service = new OrgService(mock(DSLContext.class));

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
        TenantContext.clear();
        TenantEnforcement.configure(null);
    }

    @Test
    void differentTenantsWithAllScopeShouldHaveDifferentKeys() {
        TenantEnforcement.configure(enforcingProps());
        DataScopeContext.set(new DataScopeContext.Scope(1L, DataScopeResult.all()));

        TenantContext.set(1L, false);
        String tenantOne = service.orgScopeKey();
        TenantContext.set(2L, false);
        String tenantTwo = service.orgScopeKey();

        assertEquals("T1|ALL", tenantOne);
        assertEquals("T2|ALL", tenantTwo);
        assertNotEquals(tenantOne, tenantTwo);
    }

    @Test
    void sameTenantSelfScopeKeyShouldBeStable() {
        TenantEnforcement.configure(enforcingProps());
        DataScopeContext.set(new DataScopeContext.Scope(7L, DataScopeResult.self()));
        TenantContext.set(3L, false);

        String first = service.orgScopeKey();
        String second = service.orgScopeKey();

        assertEquals("T3|SELF:7", first);
        assertEquals(first, second);
        assertTrue(first.contains("SELF:7"));
    }

    @Test
    void disabledEnforcementShouldUseFixedTenantPrefix() {
        TenantEnforcement.configure(null);
        DataScopeContext.set(new DataScopeContext.Scope(7L, DataScopeResult.all()));
        TenantContext.set(9L, false);

        assertEquals("T0|ALL", service.orgScopeKey());
    }

    private static TenantProperties enforcingProps() {
        TenantProperties props = new TenantProperties();
        props.setEnabled(true);
        props.setEnforce(true);
        props.setDefaultTenantId(0L);
        props.setPlatformTables(List.of("sys_menu", "sys_dict_data"));
        return props;
    }
}
