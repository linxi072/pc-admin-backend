package com.acme.scaffold.system.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.event.EventPublisher;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.security.password.PasswordHistoryService;
import com.acme.scaffold.security.permission.ColumnPermissionResourceRegistry;
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.org.domain.model.SysOrg;
import com.acme.scaffold.system.post.service.PostService;
import com.acme.scaffold.system.role.domain.model.SysRole;
import com.acme.scaffold.system.role.service.RoleColumnPermissionService;
import com.acme.scaffold.system.user.domain.CreateUserRequest;
import com.acme.scaffold.system.user.domain.model.SysUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 用户名唯一键并发兜底回归：数据库冲突须转成与预校验一致的业务 409，而非通用异常。 */
class UserServiceUsernameConflictTest {

    private static final String STRONG_PASSWORD = "Str0ng!Pass";

    private static CreateUserRequest request() {
        return new CreateUserRequest("alice", STRONG_PASSWORD, "Alice",
                null, null, List.of(1L), null, List.of(1L), null, null);
    }

    private static UserService service() {
        return new UserService(mock(DSLContext.class), encoder(), mock(TokenVersionService.class),
                mock(PasswordHistoryService.class), mock(PostService.class),
                mock(RoleColumnPermissionService.class), mock(ColumnPermissionResourceRegistry.class),
                mock(SecurityContextFacade.class), mock(EventPublisher.class));
    }

    private static PasswordEncoder encoder() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("{mock}hash");
        return encoder;
    }

    @Test
    void concurrentCreateShouldReturnConflict() {
        UserService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            // 请求未带手机/邮箱（无预校验 count）：预校验 existsByUsername 放行，冲突定位重查命中用户名
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any()))
                    .thenReturn(0L, 1L);
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_ROLE),
                    eq(SysRole.class), eq(1L))).thenReturn(activeRole());
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_ORG),
                    eq(SysOrg.class), eq(1L))).thenReturn(new SysOrg());
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_USER),
                    any(SysUser.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request()));

            assertEquals("用户名已存在", ex.getMessage());
        }
    }

    @Test
    void normalCreateShouldNotBeSwallowedByConflictGuard() {
        UserService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any())).thenReturn(0L);
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_ROLE),
                    eq(SysRole.class), eq(1L))).thenReturn(activeRole());
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_ORG),
                    eq(SysOrg.class), eq(1L))).thenReturn(new SysOrg());

            assertDoesNotThrow(() -> service.create(request()));
        }
    }

    private static SysRole activeRole() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setStatus("ACTIVE");
        return role;
    }
}
