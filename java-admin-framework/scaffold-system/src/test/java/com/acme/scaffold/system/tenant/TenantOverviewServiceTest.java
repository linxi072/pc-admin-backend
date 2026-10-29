package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.tenant.TenantIsolationTableViolation;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.jooq.dsl.TenantTables;
import com.acme.scaffold.system.tenant.dto.TenantDataOverview;
import com.acme.scaffold.system.tenant.dto.TenantOverviewItem;
import com.acme.scaffold.system.tenant.dto.TenantOverviewView;
import com.acme.scaffold.system.tenant.dto.TenantView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantOverviewServiceTest {

    @Mock
    private TenantService tenantService;
    @Mock
    private TenantGovernanceService governanceService;
    @Mock
    private TenantIsolationAuditService isolationAuditService;
    @Mock
    private TenantProperties tenantProperties;

    @InjectMocks
    private TenantOverviewService service;

    private static TenantView tenant(long id, String code, String name, String status) {
        return new TenantView(id, code, name, null, null, null, status, null, null, null, null);
    }

    @Test
    @DisplayName("buildOverview 聚合租户元数据、数据量与隔离自检，并正确合并每租户行数")
    void buildOverview_aggregatesTenantsAndAudit() {
        when(tenantProperties.isEnforce()).thenReturn(true);
        when(tenantService.list()).thenReturn(List.of(
                tenant(1L, "t1", "租户一", "ENABLED"),
                tenant(2L, "t2", "租户二", "DISABLED")));
        when(governanceService.dataOverview()).thenReturn(List.of(
                new TenantDataOverview(1L, "租户一", 10L, Map.of("sys_user", 10L)),
                new TenantDataOverview(2L, "租户二", 5L, Map.of("sys_user", 5L))));
        when(isolationAuditService.scanViolations()).thenReturn(
                List.of(new TenantIsolationTableViolation("sys_user", 0L)));

        TenantOverviewView v = service.buildOverview();

        assertTrue(v.enforce());
        assertEquals(2, v.tenantCount());
        assertEquals(1, v.enabledTenantCount());
        assertTrue(v.businessTableCount() > 0);
        assertTrue(v.businessTableCount() == TenantTables.businessTableNames().size());
        assertNotNull(v.isolationAudit());
        // sys_user 未归属行数=0 → 可安全开启 enforce
        assertTrue(v.isolationAudit().safeToEnable());
        assertEquals(2, v.tenants().size());

        TenantOverviewItem t1 = v.tenants().stream()
                .filter(t -> t.tenantId() == 1L).findFirst().orElseThrow();
        assertEquals("租户一", t1.tenantName());
        assertEquals("ENABLED", t1.status());
        assertEquals(10L, t1.totalRows());
        assertEquals(10L, t1.tableCounts().get("sys_user"));
    }

    @Test
    @DisplayName("buildOverview 对各子源 fail-open：任一来源异常不阻断整页总览")
    void buildOverview_failOpenWhenSubSourceThrows() {
        when(tenantProperties.isEnforce()).thenReturn(false);
        when(tenantService.list()).thenThrow(new RuntimeException("db down"));
        when(governanceService.dataOverview()).thenThrow(new RuntimeException("db down"));
        when(isolationAuditService.scanViolations()).thenThrow(new RuntimeException("db down"));

        TenantOverviewView v = service.buildOverview();

        // 各子源回落默认值，但 enforce 与 businessTableCount 仍可用
        assertFalse(v.enforce());
        assertEquals(0, v.tenantCount());
        assertEquals(0, v.enabledTenantCount());
        assertNull(v.isolationAudit());
        assertNotNull(v.tenants());
        assertEquals(0, v.tenants().size());
        assertTrue(v.businessTableCount() > 0);
    }
}
