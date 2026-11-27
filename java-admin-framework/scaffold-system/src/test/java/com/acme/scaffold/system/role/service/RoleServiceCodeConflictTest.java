package com.acme.scaffold.system.role.service;

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
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.role.domain.CreateRoleRequest;
import com.acme.scaffold.system.role.domain.model.SysRole;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 角色编码唯一键并发兜底回归：数据库冲突须转成与预校验一致的业务 409，正常路径不受影响。 */
class RoleServiceCodeConflictTest {

    private static RoleService service() {
        return new RoleService(mock(DSLContext.class), mock(TokenVersionService.class));
    }

    private static CreateRoleRequest request() {
        return new CreateRoleRequest("FINANCE", "财务角色", "BUSINESS", "ACTIVE", 0,
                Set.of(1L), Set.of(1L));
    }

    @Test
    void concurrentCreateShouldReturnConflict() {
        RoleService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.countGlobal(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.fetchList(any(DSLContext.class), eq(SqlTables.SYS_MENU),
                    eq(com.acme.scaffold.system.menu.domain.model.SysMenu.class), any()))
                    .thenReturn(java.util.List.of());
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_ROLE),
                    any(SysRole.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request()));

            assertEquals("角色编码已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        RoleService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.countGlobal(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.fetchList(any(DSLContext.class), eq(SqlTables.SYS_MENU),
                    eq(com.acme.scaffold.system.menu.domain.model.SysMenu.class), any()))
                    .thenReturn(java.util.List.of());

            assertDoesNotThrow(() -> service.create(request()));
        }
    }
}
