package com.acme.scaffold.security.oauth2;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.permission.PermissionService;
import com.acme.scaffold.security.user.UserAccount;
import com.acme.scaffold.security.user.UserAccountDataPort;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** OAuth2 password 授权锁定状态单测：与交互式登录共享到期临时锁语义。 */
class OAuth2UserAuthenticatorTest {

    @Test
    void expiredTemporaryLockShouldAllowAuthentication() {
        OAuth2UserAuthenticator authenticator = authenticator(user("LOCKED", LocalDateTime.now().minusMinutes(1)));

        assertNotNull(authenticator.authenticate("alice", "pw"));
    }

    @Test
    void manualLockWithoutExpiryShouldRejectAuthentication() {
        OAuth2UserAuthenticator authenticator = authenticator(user("LOCKED", null));

        assertThrows(BusinessException.class, () -> authenticator.authenticate("alice", "pw"));
    }

    @Test
    void futureTemporaryLockShouldRejectAuthentication() {
        OAuth2UserAuthenticator authenticator = authenticator(user("LOCKED", LocalDateTime.now().plusMinutes(15)));

        assertThrows(BusinessException.class, () -> authenticator.authenticate("alice", "pw"));
    }

    @Test
    void disabledAccountShouldRejectAuthentication() {
        OAuth2UserAuthenticator authenticator = authenticator(user("DISABLED", null));

        assertThrows(BusinessException.class, () -> authenticator.authenticate("alice", "pw"));
    }

    private static OAuth2UserAuthenticator authenticator(UserAccount user) {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PermissionService permissions = mock(PermissionService.class);
        UserAccountDataPort accountPort = mock(UserAccountDataPort.class);
        when(encoder.matches("pw", "hash")).thenReturn(true);
        when(permissions.getPermissions(42L)).thenReturn(Set.of("read"));
        when(permissions.getRoleCodes(42L)).thenReturn(Set.of("USER"));
        when(accountPort.loadByUsername("alice")).thenReturn(user);
        return new OAuth2UserAuthenticator(encoder, permissions, accountPort);
    }

    private static UserAccount user(String status, LocalDateTime lockedUntil) {
        return new UserAccount(42L, "alice", "Alice", 1L, status, "hash", 1, 0,
                null, null, lockedUntil, 5, null);
    }
}
