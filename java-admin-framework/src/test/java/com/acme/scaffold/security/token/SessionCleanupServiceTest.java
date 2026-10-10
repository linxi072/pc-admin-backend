package com.acme.scaffold.security.token;

import com.acme.scaffold.security.config.JwtProperties;
import org.jooq.DSLContext;
import org.jooq.Table;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCleanupServiceTest {

    @Mock
    private DSLContext dsl;
    @Mock
    private JwtProperties jwtProps;

    @Test
    void skipsAllDbAccessWhenDisabled() {
        when(jwtProps.isSessionCleanupEnabled()).thenReturn(false);
        SessionCleanupService svc = new SessionCleanupService(dsl, jwtProps);

        svc.runCleanup();

        // 禁用时不应触发任何数据库更新
        verify(dsl, never()).update(any(Table.class));
    }
}
