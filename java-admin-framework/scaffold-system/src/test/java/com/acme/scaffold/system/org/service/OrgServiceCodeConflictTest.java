package com.acme.scaffold.system.org.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.org.domain.CreateOrgRequest;
import com.acme.scaffold.system.org.domain.UpdateOrgRequest;
import com.acme.scaffold.system.org.domain.model.SysOrg;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 机构编码唯一键并发兜底回归：数据库冲突须转成与预校验一致的业务 409，正常路径不受影响。 */
class OrgServiceCodeConflictTest {

    private static OrgService service() {
        return new OrgService(mock(DSLContext.class));
    }

    private static CreateOrgRequest createRequest() {
        return new CreateOrgRequest(0L, "FINANCE", "财务部", "DEPARTMENT", 1, 1L, "ACTIVE");
    }

    @Test
    void concurrentCreateShouldReturnConflict() {
        OrgService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_ORG),
                    any(SysOrg.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(createRequest()));

            assertEquals("机构编码已存在", ex.getMessage());
        }
    }

    @Test
    void concurrentCodeChangeOnUpdateShouldReturnConflict() {
        OrgService service = service();

        SysOrg existing = new SysOrg();
        existing.setId(7L);
        existing.setParentId(0L);
        existing.setOrgCode("OLD_CODE");
        existing.setOrgName("旧名称");
        existing.setAncestors("0");
        existing.setStatus("ACTIVE");

        UpdateOrgRequest req = new UpdateOrgRequest(0L, "NEW_CODE", "新名称", "DEPARTMENT", 1, 1L, null);

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_ORG),
                    eq(SysOrg.class), eq(7L))).thenReturn(existing);
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.updateById(any(DSLContext.class), eq(SqlTables.SYS_ORG),
                    eq(7L), any(SysOrg.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(7L, req));

            assertEquals("机构编码已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        OrgService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);

            assertDoesNotThrow(() -> service.create(createRequest()));
        }
    }
}
