package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.tenant.TenantIsolationAuditReport;
import com.acme.scaffold.common.tenant.TenantIsolationTableViolation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantIsolationAuditControllerTest {

    @Test
    @DisplayName("audit() 返回封装成功的自检报告，并入真实业务表总数")
    void auditReturnsReport() {
        TenantIsolationAuditService service = mock(TenantIsolationAuditService.class);
        when(service.scanViolations()).thenReturn(List.of(
                new TenantIsolationTableViolation("sys_user", 0L),
                new TenantIsolationTableViolation("sys_order", 3L)));
        TenantIsolationAuditController controller = new TenantIsolationAuditController(service);

        Result<TenantIsolationAuditReport> result = controller.audit();
        assertTrue(result.isSuccess());
        assertNotNull(result.data());
        // totalBusinessTables 取真实业务表数（非入参清单长度）
        assertTrue(result.data().totalBusinessTables() > 0);
        assertEquals(3L, result.data().totalViolatedRows());
        assertFalse(result.data().safeToEnable());
    }
}
