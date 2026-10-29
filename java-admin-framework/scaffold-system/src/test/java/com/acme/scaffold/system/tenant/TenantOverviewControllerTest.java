package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.system.tenant.dto.TenantOverviewView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantOverviewControllerTest {

    @Test
    @DisplayName("overview() 经 Result 包络返回聚合快照")
    void overviewReturnsAggregatedView() {
        TenantOverviewService service = mock(TenantOverviewService.class);
        TenantOverviewView view = new TenantOverviewView(
                false, 1, 1, 5, null, List.of(), LocalDateTime.now());
        when(service.buildOverview()).thenReturn(view);

        TenantOverviewController controller = new TenantOverviewController(service);
        Result<TenantOverviewView> result = controller.overview();

        assertTrue(result.isSuccess());
        assertFalse(result.data().enforce());
        assertEquals(1, result.data().tenantCount());
        assertEquals(1, result.data().enabledTenantCount());
        assertEquals(5, result.data().businessTableCount());
    }
}
