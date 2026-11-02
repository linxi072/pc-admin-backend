package com.acme.scaffold.security.service;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.security.captcha.CaptchaProperties;
import com.acme.scaffold.security.captcha.CaptchaService;
import com.acme.scaffold.security.dto.LoginCommand;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.jwt.JwtProvider;
import com.acme.scaffold.security.log.LoginLogRecorder;
import com.acme.scaffold.security.online.LoginRestrictionDataPort;
import com.acme.scaffold.security.online.OnlineSessionDataPort;
import com.acme.scaffold.security.password.PasswordHistoryService;
import com.acme.scaffold.security.permission.PermissionService;
import com.acme.scaffold.security.token.service.RefreshTokenService;
import com.acme.scaffold.security.user.UserAccount;
import com.acme.scaffold.security.user.UserAccountDataPort;
import com.acme.scaffold.security.user.UserAccountPatch;
import com.acme.scaffold.security.vo.TokenView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 解耦后（经 UserAccountDataPort / LoginRestrictionDataPort / OnlineSessionDataPort）的回归测试。
 * 聚焦于登录成功链路与失败累计链路，验证 security 不再直连 system.entity / system.service。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock private UserAccountDataPort accountPort;
    @Mock private PermissionService permissionService;
    @Mock private JwtProvider jwtProvider;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProperties jwtProperties;
    @Mock private SecurityContextFacade securityContextFacade;
    @Mock private CaptchaService captchaService;
    @Mock private CaptchaProperties captchaProperties;
    @Mock private PasswordHistoryService passwordHistoryService;
    @Mock private LoginLogRecorder loginLogRecorder;
    @Mock private LoginRestrictionDataPort restrictionPort;
    @Mock private OnlineSessionDataPort onlineSessionPort;

    private AuthService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new AuthService(accountPort, permissionService, jwtProvider, refreshTokenService,
                passwordEncoder, jwtProperties, securityContextFacade, captchaService, captchaProperties,
                passwordHistoryService, loginLogRecorder, restrictionPort, onlineSessionPort);
        // AuthService 依赖 Spring 自注入代理 self 进入内部短事务；单元测试中手动回填 self=this
        var selfField = AuthService.class.getDeclaredField("self");
        selfField.setAccessible(true);
        selfField.set(service, service);

        when(captchaProperties.isEnabled()).thenReturn(false);
        when(jwtProperties.getMaxConcurrentSessions()).thenReturn(0);
        when(jwtProperties.getMaxLoginFailures()).thenReturn(5);
        when(jwtProperties.getLockDuration()).thenReturn(Duration.ofMinutes(10));
        when(jwtProperties.getLockDurationMax()).thenReturn(Duration.ofHours(2));
        when(jwtProperties.getAccessTokenTtl()).thenReturn(Duration.ofHours(2));
        when(permissionService.getPermissions(any())).thenReturn(Set.of("sys:user:read"));
        when(permissionService.getRoleCodes(any())).thenReturn(Set.of("ADMIN"));
    }

    private UserAccount sampleAccount() {
        return new UserAccount(1L, "alice", "Alice", 0L, "ENABLED",
                "$2a$10$enc", 1, 0, "13800000000", "a@b.com", null, 0, null);
    }

    @Test
    void login_success_returnsTokensAndRegistersSession() {
        UserAccount user = sampleAccount();
        when(accountPort.loadByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("pw", user.passwordHash())).thenReturn(true);
        when(restrictionPort.findActive(1L)).thenReturn(null);
        when(jwtProvider.generateAccessToken(any(CurrentPrincipal.class))).thenReturn("access-token");
        when(refreshTokenService.issue(1L, "web", "dev-1", "UA", "127.0.0.1")).thenReturn("refresh-token");

        LoginCommand cmd = new LoginCommand("alice", "pw", null, null);
        TokenView view = service.login(cmd, "127.0.0.1", "UA", "dev-1");

        assertEquals("access-token", view.accessToken());
        assertEquals("refresh-token", view.refreshToken());
        verify(onlineSessionPort).registerSession(eq(1L), eq("alice"), eq("Alice"), eq("dev-1"),
                eq("web"), eq("127.0.0.1"), eq("UA"));
        // 失败计数为 0 -> resetLoginFailure 仅更新 last_login_at
        verify(accountPort, times(1)).updateById(eq(1L), any(UserAccountPatch.class));
        verify(loginLogRecorder).record(eq("alice"), eq(1L), eq(true), eq((String) null),
                eq("127.0.0.1"), eq("UA"));
    }

    @Test
    void login_wrongPassword_accumulatesFailureAndThrows() {
        UserAccount user = sampleAccount();
        when(accountPort.loadByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("bad", user.passwordHash())).thenReturn(false);

        LoginCommand cmd = new LoginCommand("alice", "bad", null, null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(cmd, "127.0.0.1", "UA", "dev-1"));

        assertEquals(SecurityErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
        // 失败计数 0 -> 1，未达锁定阈值，仅写回 failed_login_count
        verify(accountPort).updateById(eq(1L), argThat(p -> p.failedLoginCount() != null
                && p.failedLoginCount() == 1 && !"LOCKED".equals(p.status())));
        verify(onlineSessionPort, never()).registerSession(any(), any(), any(), any(), any(), any(), any());
        verify(loginLogRecorder).record(eq("alice"), eq(1L), eq(false),
                eq(SecurityErrorCode.BAD_CREDENTIALS.code()), eq("127.0.0.1"), eq("UA"));
    }

    @Test
    void login_activeRestriction_blocksWithMessage() {
        UserAccount user = sampleAccount();
        when(accountPort.loadByUsername("alice")).thenReturn(user);
        when(passwordEncoder.matches("pw", user.passwordHash())).thenReturn(true);
        when(restrictionPort.findActive(1L)).thenReturn(
                new com.acme.scaffold.security.online.LoginRestriction(null));

        LoginCommand cmd = new LoginCommand("alice", "pw", null, null);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.login(cmd, "127.0.0.1", "UA", "dev-1"));

        assertEquals(SecurityErrorCode.ACCOUNT_RESTRICTED, ex.getErrorCode());
        verify(onlineSessionPort, never()).registerSession(any(), any(), any(), any(), any(), any(), any());
    }
}
