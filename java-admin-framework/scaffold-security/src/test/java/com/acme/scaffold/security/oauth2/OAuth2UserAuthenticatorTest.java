package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.permission.PermissionService;
import com.acme.scaffold.security.user.UserAccount;
import com.acme.scaffold.security.user.UserAccountDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2UserAuthenticatorTest {

    @Mock
    private UserAccountDataPort accountPort;
    @Mock
    private PermissionService permissionService;
    @Mock
    private PasswordEncoder passwordEncoder;

    private OAuth2UserAuthenticator authenticator;

    @BeforeEach
    void init() {
        authenticator = new OAuth2UserAuthenticator(passwordEncoder, permissionService, accountPort);
    }

    private UserAccount account(String status) {
        return new UserAccount(42L, "alice", "Alice", 1L, status, "hash", 3, 0,
                "13800000000", "a@x.com", null, 0, null);
    }

    @Test
    void authenticate_success_aggregatesPermissionsAndRoles() {
        UserAccount u = account("ENABLED");
        when(accountPort.loadByUsername("alice")).thenReturn(u);
        when(passwordEncoder.matches("pw", "hash")).thenReturn(true);
        when(permissionService.getPermissions(42L)).thenReturn(Set.of("sys:user:read"));
        when(permissionService.getRoleCodes(42L)).thenReturn(Set.of("ADMIN"));

        OAuth2UserAuthenticator.AuthenticatedUser result = authenticator.authenticate("alice", "pw");

        assertEquals(42L, result.userId());
        assertEquals("alice", result.username());
        assertEquals(3, result.tokenVersion());
        assertEquals(Set.of("sys:user:read"), result.authorities());
        assertEquals(Set.of("ADMIN"), result.roles());
    }

    @Test
    void authenticate_unknownUser_throwsBadCredentials() {
        when(accountPort.loadByUsername("ghost")).thenReturn(null);
        BusinessException ex = assertThrows(BusinessException.class, () -> authenticator.authenticate("ghost", "pw"));
        assertEquals(SecurityErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
    }

    @Test
    void authenticate_disabled_throwsAccountDisabled() {
        when(accountPort.loadByUsername("alice")).thenReturn(account("DISABLED"));
        BusinessException ex = assertThrows(BusinessException.class, () -> authenticator.authenticate("alice", "pw"));
        assertEquals(SecurityErrorCode.ACCOUNT_DISABLED, ex.getErrorCode());
    }

    @Test
    void authenticate_locked_throwsAccountDisabled() {
        when(accountPort.loadByUsername("alice")).thenReturn(account("LOCKED"));
        BusinessException ex = assertThrows(BusinessException.class, () -> authenticator.authenticate("alice", "pw"));
        assertEquals(SecurityErrorCode.ACCOUNT_DISABLED, ex.getErrorCode());
    }

    @Test
    void authenticate_wrongPassword_throwsBadCredentials() {
        when(accountPort.loadByUsername("alice")).thenReturn(account("ENABLED"));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);
        BusinessException ex = assertThrows(BusinessException.class, () -> authenticator.authenticate("alice", "bad"));
        assertEquals(SecurityErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
    }

    @Test
    void authenticate_blankInput_throwsBadCredentials() {
        BusinessException ex = assertThrows(BusinessException.class, () -> authenticator.authenticate("", "pw"));
        assertEquals(SecurityErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
    }
}
