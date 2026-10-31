package com.acme.scaffold.security.token.service;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenVersionServiceTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private DSLContext dsl;
    @Mock
    private TokenVersionDataPort port;

    private TokenVersionService service;

    @BeforeEach
    void init() {
        service = new TokenVersionService(dsl, port);
    }

    @Test
    void bumpRoleHolders_delegatesToPortAndBumpsUsers() {
        Set<Long> roles = Set.of(11L, 12L);
        when(port.userIdsByRoleIds(roles)).thenReturn(List.of(101L, 102L));

        service.bumpRoleHolders(roles);

        verify(port).userIdsByRoleIds(eq(roles));
    }

    @Test
    void bumpRoleHolders_emptyRolesSkipsPort() {
        service.bumpRoleHolders(Set.of());
        verify(port, never()).userIdsByRoleIds(any());
    }
}
