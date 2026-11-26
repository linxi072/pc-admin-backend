package com.acme.scaffold.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.acme.scaffold.security.user.UserAccount;
import com.acme.scaffold.security.user.UserAccountDataPort;
import com.acme.scaffold.security.user.UserAccountPatch;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** AuthService 锁定状态接线单测：到期临时锁放行、成功登录清理持久化 LOCKED 状态。 */
class AuthServiceLockoutTest {

    @Test
    void resetLoginFailureShouldPersistActiveStatusInBothBranches() throws Exception {
        UserAccountDataPort accountPort = mock(UserAccountDataPort.class);
        AuthService service = service(accountPort);
        Method reset = AuthService.class.getDeclaredMethod("resetLoginFailure", UserAccount.class);
        reset.setAccessible(true);

        reset.invoke(service, user("LOCKED", LocalDateTime.now().minusMinutes(1), 5));
        reset.invoke(service, user("LOCKED", LocalDateTime.now().minusMinutes(1), 0));

        ArgumentCaptor<UserAccountPatch> patches = ArgumentCaptor.forClass(UserAccountPatch.class);
        verify(accountPort, times(2)).updateById(eq(42L), patches.capture());
        assertEquals("ACTIVE", patches.getAllValues().get(0).status());
        assertEquals(0, patches.getAllValues().get(0).failedLoginCount());
        assertEquals("ACTIVE", patches.getAllValues().get(1).status());
        assertNull(patches.getAllValues().get(1).failedLoginCount());
        assertNull(patches.getAllValues().get(1).lockedUntil());
    }

    @Test
    void serviceShouldUseExpiryAwareLockDecision() throws Exception {
        AuthService service = service(mock(UserAccountDataPort.class));
        Method isLocked = AuthService.class.getDeclaredMethod("isLocked", UserAccount.class);
        isLocked.setAccessible(true);

        assertFalse((Boolean) isLocked.invoke(service,
                user("LOCKED", LocalDateTime.now().minusMinutes(1), 5)));
        assertTrue((Boolean) isLocked.invoke(service, user("LOCKED", null, 5)));
        assertTrue((Boolean) isLocked.invoke(service,
                user("ACTIVE", LocalDateTime.now().plusMinutes(1), 0)));
    }

    private static AuthService service(UserAccountDataPort accountPort) {
        return new AuthService(accountPort, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    private static UserAccount user(String status, LocalDateTime lockedUntil, int failedCount) {
        return new UserAccount(42L, "demo", "演示用户", 1L, status, "hash", 1, 0,
                null, null, lockedUntil, failedCount, null);
    }
}
