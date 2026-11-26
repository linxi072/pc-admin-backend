package com.acme.scaffold.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.log.LoginLogRecorder;
import com.acme.scaffold.security.online.OnlineSessionDataPort;
import com.acme.scaffold.security.password.PasswordHistoryService;
import com.acme.scaffold.security.token.service.RefreshTokenService;
import com.acme.scaffold.security.user.UserAccount;
import com.acme.scaffold.security.user.UserAccountDataPort;
import com.acme.scaffold.security.user.UserAccountPatch;
import java.lang.reflect.Field;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 会话无关改密安全回归：旧密码错误必须累计失败次数并可触发渐进式锁定。 */
class AuthChangePasswordSecurityTest {

    private UserAccountDataPort accountPort;
    private PasswordEncoder encoder;
    private JwtProperties jwtProperties;
    private PasswordHistoryService passwordHistory;
    private RefreshTokenService refreshTokens;
    private OnlineSessionDataPort onlineSessions;
    private LoginLogRecorder loginLog;
    private AuthService service;

    @BeforeEach
    void setUp() throws Exception {
        accountPort = mock(UserAccountDataPort.class);
        encoder = mock(PasswordEncoder.class);
        jwtProperties = mock(JwtProperties.class);
        passwordHistory = mock(PasswordHistoryService.class);
        refreshTokens = mock(RefreshTokenService.class);
        onlineSessions = mock(OnlineSessionDataPort.class);
        loginLog = mock(LoginLogRecorder.class);

        service = new AuthService(accountPort, null, null, refreshTokens, encoder, jwtProperties, null,
                null, null, passwordHistory, loginLog, null, onlineSessions);
        Field self = AuthService.class.getDeclaredField("self");
        self.setAccessible(true);
        self.set(service, service);

        when(accountPort.loadByUsername("demo")).thenReturn(user(0));
        when(accountPort.loadById(42L)).thenReturn(user(0));
        when(encoder.matches("wrong", "hash")).thenReturn(false);
        when(encoder.matches("right", "hash")).thenReturn(true);
        when(encoder.encode("NewPass123!")).thenReturn("newHash");
        when(jwtProperties.getMaxLoginFailures()).thenReturn(5);
        when(jwtProperties.getLockDuration()).thenReturn(Duration.ofMinutes(5));
        when(jwtProperties.getLockDurationMax()).thenReturn(Duration.ofHours(1));
        when(passwordHistory.isReused(42L, "NewPass123!")).thenReturn(false);
    }

    @Test
    void wrongOldPasswordShouldRecordFailure() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.changePassword("demo", "wrong", "NewPass123!", "203.0.113.9", "ua"));

        assertEquals(SecurityErrorCode.BAD_CREDENTIALS.code(), ex.getErrorCode().code());
        ArgumentCaptor<UserAccountPatch> patches = ArgumentCaptor.forClass(UserAccountPatch.class);
        verify(accountPort).updateById(eq(42L), patches.capture());
        assertEquals(1, patches.getValue().failedLoginCount());
        verify(loginLog).record(eq("demo"), eq(42L), eq(false), eq(SecurityErrorCode.BAD_CREDENTIALS.code()),
                eq("203.0.113.9"), eq("ua"));
        verify(refreshTokens, never()).revokeByUser(any());
    }

    @Test
    void repeatedWrongPasswordShouldReachLockoutThreshold() {
        when(accountPort.loadById(42L)).thenReturn(user(4));

        assertThrows(BusinessException.class,
                () -> service.changePassword("demo", "wrong", "NewPass123!", "203.0.113.9", "ua"));

        ArgumentCaptor<UserAccountPatch> patches = ArgumentCaptor.forClass(UserAccountPatch.class);
        verify(accountPort).updateById(eq(42L), patches.capture());
        assertEquals(5, patches.getValue().failedLoginCount());
        assertEquals("LOCKED", patches.getValue().status());
        assertNotNull(patches.getValue().lockedUntil());
    }

    @Test
    void correctOldPasswordShouldCommitChangeWithoutFailureCount() {
        service.changePassword("demo", "right", "NewPass123!", "203.0.113.9", "ua");

        ArgumentCaptor<UserAccountPatch> patches = ArgumentCaptor.forClass(UserAccountPatch.class);
        verify(accountPort).updateById(eq(42L), patches.capture());
        assertEquals("newHash", patches.getValue().passwordHash());
        assertEquals(0, patches.getValue().failedLoginCount());
        assertEquals(2, patches.getValue().tokenVersion());
        verify(passwordHistory).record(42L, "newHash");
        verify(refreshTokens).revokeByUser(42L);
        verify(onlineSessions).markOfflineByUser(42L, OnlineSessionDataPort.REASON_PASSWORD_CHANGED);
    }

    private static UserAccount user(int failedCount) {
        return new UserAccount(42L, "demo", "演示用户", 1L, "ACTIVE", "hash", 1, 0,
                null, null, null, failedCount, null);
    }
}
