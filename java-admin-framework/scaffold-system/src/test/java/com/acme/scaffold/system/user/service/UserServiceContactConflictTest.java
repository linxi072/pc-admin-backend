package com.acme.scaffold.system.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.event.EventPublisher;
import com.acme.scaffold.common.exception.BusinessException;
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
import com.acme.scaffold.system.user.domain.UpdateUserRequest;
import com.acme.scaffold.system.user.domain.model.SysUser;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 用户手机号 / 邮箱唯一键并发兜底回归：数据库冲突须重查定位为与预校验一致的业务 409。 */
class UserServiceContactConflictTest {

    private static final String STRONG_PASSWORD = "Str0ng!Pass";

    private static CreateUserRequest createRequest() {
        return new CreateUserRequest("alice", STRONG_PASSWORD, "Alice",
                "13900000001", "alice@example.com", List.of(1L), null, List.of(1L), null, null);
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

    private static void stubLookups(MockedStatic<SqlWriters> writers) {
        writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_ROLE),
                eq(SysRole.class), eq(1L))).thenReturn(activeRole());
        writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_ORG),
                eq(SysOrg.class), eq(1L))).thenReturn(new SysOrg());
    }

    @Test
    void concurrentCreateWithDuplicateUsernameShouldKeepUsernameMessage() {
        UserService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            stubLookups(writers);
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any()))
                    .thenReturn(0L, 0L, 0L, 1L);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_USER),
                    any(SysUser.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(createRequest()));

            assertEquals("用户名已存在", ex.getMessage());
        }
    }

    @Test
    void concurrentCreateWithDuplicateMobileShouldReturnMobileMessage() {
        UserService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            stubLookups(writers);
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any()))
                    .thenReturn(0L, 0L, 0L, 0L, 1L);
            writers.when(() -> SqlWriters.insert(any(DSLContext.class), eq(SqlTables.SYS_USER),
                    any(SysUser.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.create(createRequest()));

            assertEquals("该手机号已被其他账号绑定", ex.getMessage());
        }
    }

    @Test
    void concurrentUpdateWithDuplicateEmailShouldReturnEmailMessage() {
        UserService service = service();

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            stubLookups(writers);
            writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_USER),
                    eq(SysUser.class), eq(1L))).thenReturn(existingUser());
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any()))
                    .thenReturn(0L, 0L, 0L, 0L, 1L);
            writers.when(() -> SqlWriters.updateById(any(DSLContext.class), eq(SqlTables.SYS_USER),
                    eq(1L), any(SysUser.class)))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L,
                    new UpdateUserRequest(null, "13900000002", "new@example.com",
                            null, null, null, null, null, null)));

            assertEquals("该邮箱已被其他账号绑定", ex.getMessage());
        }
    }

    private static SysUser existingUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("alice");
        user.setStatus("ACTIVE");
        return user;
    }

    private static SysRole activeRole() {
        SysRole role = new SysRole();
        role.setId(1L);
        role.setStatus("ACTIVE");
        return role;
    }
}
