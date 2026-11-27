package com.acme.scaffold.system.profile.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.profile.domain.BindContactRequest;
import com.acme.scaffold.system.user.domain.model.SysUser;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.dao.DataIntegrityViolationException;

/** 个人中心联系方式唯一键并发兜底回归：数据库冲突须重查定位为可读的业务 409。 */
class ProfileServiceContactConflictTest {

    private static final Long USER_ID = 1L;

    private static ProfileService service(SecurityContextFacade security) {
        return new ProfileService(mock(DSLContext.class), security,
                mock(ProfileDeviceService.class), mock(ProfilePasswordService.class));
    }

    private static SecurityContextFacade security() {
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        when(security.requireCurrentPrincipal()).thenReturn(new CurrentPrincipal(
                USER_ID, "alice", "Alice", 0L, null, 1, Set.of(), Set.of()));
        return security;
    }

    private static void stubUser(MockedStatic<SqlWriters> writers) {
        SysUser user = new SysUser();
        user.setId(USER_ID);
        user.setUsername("alice");
        user.setStatus("ACTIVE");
        writers.when(() -> SqlWriters.fetchById(any(DSLContext.class), eq(SqlTables.SYS_USER),
                eq(SysUser.class), eq(USER_ID))).thenReturn(user);
    }

    @Test
    void concurrentBindMobileShouldReturnMobileMessage() {
        ProfileService service = service(security());

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            stubUser(writers);
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any()))
                    .thenReturn(0L, 1L);
            writers.when(() -> SqlWriters.updateByIdWithNulls(any(DSLContext.class), eq(SqlTables.SYS_USER),
                    eq(USER_ID), any(SysUser.class), any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.bindContact(
                    new BindContactRequest("MOBILE", "13900000001")));

            assertEquals("该手机号已被其他账号绑定", ex.getMessage());
        }
    }

    @Test
    void concurrentBindEmailShouldReturnEmailMessage() {
        ProfileService service = service(security());

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            stubUser(writers);
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any()))
                    .thenReturn(0L, 1L);
            writers.when(() -> SqlWriters.updateByIdWithNulls(any(DSLContext.class), eq(SqlTables.SYS_USER),
                    eq(USER_ID), any(SysUser.class), any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.bindContact(
                    new BindContactRequest("EMAIL", "new@example.com")));

            assertEquals("该邮箱已被其他账号绑定", ex.getMessage());
        }
    }

    @Test
    void unresolvedConflictShouldFallBackToGenericMessage() {
        ProfileService service = service(security());

        try (MockedStatic<SqlWriters> writers = mockStatic(SqlWriters.class)) {
            stubUser(writers);
            writers.when(() -> SqlWriters.count(any(DSLContext.class), any(), any()))
                    .thenReturn(0L, 0L);
            writers.when(() -> SqlWriters.updateByIdWithNulls(any(DSLContext.class), eq(SqlTables.SYS_USER),
                    eq(USER_ID), any(SysUser.class), any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate key"));

            BusinessException ex = assertThrows(BusinessException.class, () -> service.bindContact(
                    new BindContactRequest("MOBILE", "13900000001")));

            assertEquals("联系方式已被其他请求占用，请重试", ex.getMessage());
        }
    }
}
