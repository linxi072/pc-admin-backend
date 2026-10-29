package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.permission.PermissionService;
import com.acme.scaffold.system.entity.SysUserDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jooq.DSLContext;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("OAuth2 用户认证（password 授权）")
class OAuth2UserAuthenticatorTest {

    @Test
    @DisplayName("用户名/密码正确 → 返回聚合权限的主体")
    void authenticate_success() {
        DSLContext dsl = mock(DSLContext.class);
        org.springframework.security.crypto.password.PasswordEncoder pe = mock(
                org.springframework.security.crypto.password.PasswordEncoder.class);
        PermissionService perm = mock(PermissionService.class);

        SysUserDO user = new SysUserDO();
        user.setId(42L);
        user.setUsername("alice");
        user.setStatus("ENABLED");
        user.setPasswordHash("hash");
        user.setTokenVersion(3);

        try (MockedStatic<JooqWriters> m = mockStatic(JooqWriters.class)) {
            m.when(() -> JooqWriters.fetchOne(eq(dsl), any(), eq(SysUserDO.class), any())).thenReturn(user);
            when(pe.matches("pw", "hash")).thenReturn(true);
            when(perm.getPermissions(42L)).thenReturn(Set.of("system:user:read"));
            when(perm.getRoleCodes(42L)).thenReturn(Set.of("ADMIN"));

            OAuth2UserAuthenticator auth = new OAuth2UserAuthenticator(dsl, pe, perm);
            OAuth2UserAuthenticator.AuthenticatedUser u = auth.authenticate("alice", "pw");
            assertEquals(42L, u.userId());
            assertEquals("alice", u.username());
            assertEquals(3, u.tokenVersion());
            assertEquals(Set.of("system:user:read"), u.authorities());
        }
    }

    @Test
    @DisplayName("密码错误 → BAD_CREDENTIALS")
    void authenticate_badPassword() {
        DSLContext dsl = mock(DSLContext.class);
        org.springframework.security.crypto.password.PasswordEncoder pe = mock(
                org.springframework.security.crypto.password.PasswordEncoder.class);
        PermissionService perm = mock(PermissionService.class);

        SysUserDO user = new SysUserDO();
        user.setId(1L);
        user.setUsername("bob");
        user.setStatus("ENABLED");
        user.setPasswordHash("hash");

        try (MockedStatic<JooqWriters> m = mockStatic(JooqWriters.class)) {
            m.when(() -> JooqWriters.fetchOne(eq(dsl), any(), eq(SysUserDO.class), any())).thenReturn(user);
            when(pe.matches("wrong", "hash")).thenReturn(false);

            OAuth2UserAuthenticator auth = new OAuth2UserAuthenticator(dsl, pe, perm);
            BusinessException ex = assertThrows(BusinessException.class, () -> auth.authenticate("bob", "wrong"));
            assertEquals(SecurityErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
        }
    }
}
