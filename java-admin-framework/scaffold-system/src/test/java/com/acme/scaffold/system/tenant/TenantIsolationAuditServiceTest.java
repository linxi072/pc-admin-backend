package com.acme.scaffold.system.tenant;

import com.acme.scaffold.jooq.dsl.TenantTables;
import com.acme.scaffold.common.tenant.TenantIsolationTableViolation;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.jooq.exception.DataAccessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantIsolationAuditServiceTest {

    @Mock
    private DSLContext dsl;

    @InjectMocks
    private TenantIsolationAuditService service;

    @Test
    @DisplayName("scanViolations 覆盖全部业务表并按表名返回统计")
    void scansEachBusinessTable() {
        when(dsl.fetchCount(any(Table.class), any(Condition.class))).thenReturn(0);

        List<TenantIsolationTableViolation> violations = service.scanViolations();

        // 应覆盖 JooqTables 反射收集的全部业务表（新增表自动纳入）
        assertEquals(TenantTables.businessTableNames().size(), violations.size());
        assertTrue(violations.stream().allMatch(v -> v.violatedRows() == 0L));
    }

    @Test
    @DisplayName("单表查询异常时以 -1 标记检查失败，不影响其余表统计")
    void marksFailedTableAsUnknown() {
        when(dsl.fetchCount(any(Table.class), any(Condition.class)))
                .thenThrow(new DataAccessException("boom") {});

        List<TenantIsolationTableViolation> violations = service.scanViolations();

        assertEquals(TenantTables.businessTableNames().size(), violations.size());
        assertTrue(violations.stream().allMatch(v -> v.violatedRows() == -1L));
    }
}
