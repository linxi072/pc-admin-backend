package com.acme.scaffold.realtime.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.realtime.domain.RealtimeBroadcastRequest;
import com.acme.scaffold.realtime.domain.RealtimeSessionView;
import com.acme.scaffold.realtime.domain.RealtimeUserPushRequest;
import com.acme.scaffold.realtime.gateway.GatewayNode;
import com.acme.scaffold.realtime.port.RealtimeTemplatePort;
import com.acme.scaffold.realtime.port.RealtimeUserDirectoryPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** 实时推送管理可见范围回归：在线会话与推送目标不得跨租户。 */
class RealtimeAdminScopeTest {

    private GatewayNode node;
    private RealtimePushService pushService;
    private SecurityContextFacade facade;
    private RealtimeAdminService service;

    @BeforeEach
    void setUp() {
        node = mock(GatewayNode.class);
        pushService = mock(RealtimePushService.class);
        facade = mock(SecurityContextFacade.class);

        ObjectProvider<GatewayNode> nodeProvider = mock(ObjectProvider.class);
        when(nodeProvider.getIfAvailable()).thenReturn(node);
        ObjectProvider<RealtimeTemplatePort> templateProvider = mock(ObjectProvider.class);
        RealtimeUserDirectoryPort directory = mock(RealtimeUserDirectoryPort.class);
        when(directory.userIdsInTenant(7L)).thenReturn(Set.of(1L, 2L));
        ObjectProvider<RealtimeUserDirectoryPort> directoryProvider = mock(ObjectProvider.class);
        when(directoryProvider.getIfAvailable()).thenReturn(directory);
        when(facade.getCurrentPrincipal()).thenReturn(Optional.of(principal(7L)));
        when(node.onlineUserCounts()).thenReturn(Map.of(1L, 1, 2L, 2, 3L, 1));
        when(pushService.sendToUsers(any(), any(), any())).thenReturn(2);

        service = new RealtimeAdminService(nodeProvider, pushService, templateProvider, directoryProvider, facade);
    }

    @Test
    void tenantAdminShouldSeeOnlyVisibleSessions() {
        List<RealtimeSessionView> sessions = service.listSessions();

        assertEquals(List.of(1L, 2L), sessions.stream().map(RealtimeSessionView::userId).toList());
        assertEquals(3, service.onlineCount());
    }

    @Test
    void pushToOutOfScopeUserShouldBeRejected() {
        RealtimeUserPushRequest request = new RealtimeUserPushRequest(
                List.of(1L, 3L), "NOTIFICATION", "hi", null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.pushToUsers(request));

        assertEquals("目标用户不在当前数据范围内", ex.getMessage());
        verify(pushService, never()).sendToUsers(any(), any(), any());
    }

    @Test
    void pushToVisibleUsersShouldSucceed() {
        RealtimeUserPushRequest request = new RealtimeUserPushRequest(
                List.of(1L, 2L), "NOTIFICATION", "hi", null, null);

        assertEquals(2, service.pushToUsers(request).delivered());
        verify(pushService).sendToUsers(eq(List.of(1L, 2L)), eq("NOTIFICATION"), eq("hi"));
    }

    @Test
    void broadcastShouldTargetOnlyVisibleUsers() {
        assertEquals(2, service.broadcast(new RealtimeBroadcastRequest("NOTIFICATION", "hi", null, null)).delivered());

        verify(pushService).sendToUsers(eq(Set.of(1L, 2L)), eq("NOTIFICATION"), eq("hi"));
        verify(pushService, never()).broadcast(any(), any());
    }

    @Test
    void missingPrincipalShouldFailClosed() {
        when(facade.getCurrentPrincipal()).thenReturn(Optional.empty());

        assertEquals(List.of(), service.listSessions());
        assertEquals(0, service.onlineCount());
        assertEquals(0, service.broadcast(new RealtimeBroadcastRequest("NOTIFICATION", "hi", null, null)).delivered());
        verify(pushService, never()).sendToUsers(any(), any(), any());
    }

    private static CurrentPrincipal principal(Long tenantId) {
        return new CurrentPrincipal(99L, "admin", "Admin", tenantId, null, 1,
                Set.of("ADMIN"), Set.of("system:realtime:read", "system:realtime:push"));
    }
}
