package com.acme.scaffold.system.log;

import com.acme.scaffold.common.audit.AuditRetentionRules;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginLogRetentionJobTest {

    @Test
    void purgesWithCutoffBeforeNowWhenEnabled() {
        LoginLogService svc = mock(LoginLogService.class);
        LoginLogProperties props = mock(LoginLogProperties.class);
        when(props.getRetentionDays()).thenReturn(180);
        when(svc.purgeBefore(org.mockito.ArgumentMatchers.any(LocalDateTime.class))).thenReturn(42);

        new LoginLogRetentionJob(svc, props).purgeExpired();

        verify(svc).purgeBefore(argThat(c -> c.isBefore(LocalDateTime.now())));
    }

    @Test
    void skipsPurgeWhenRetentionDisabled() {
        LoginLogService svc = mock(LoginLogService.class);
        LoginLogProperties props = mock(LoginLogProperties.class);
        when(props.getRetentionDays()).thenReturn(0);

        new LoginLogRetentionJob(svc, props).purgeExpired();

        verify(svc, never()).purgeBefore(org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    }

    @Test
    void cutoffIsNullWhenRetentionNonPositive() {
        assertEquals(null, AuditRetentionRules.cutoff(0));
        assertEquals(null, AuditRetentionRules.cutoff(-1));
    }
}
