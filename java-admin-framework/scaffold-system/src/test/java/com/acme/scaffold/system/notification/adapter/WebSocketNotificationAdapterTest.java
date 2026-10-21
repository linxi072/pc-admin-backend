package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.realtime.payload.RealtimeNotificationPayload;
import com.acme.scaffold.realtime.service.RealtimePushService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class WebSocketNotificationAdapterTest {

    private final RealtimePushService pushService = mock(RealtimePushService.class);
    private final WebSocketNotificationAdapter adapter = new WebSocketNotificationAdapter(pushService);

    @Test
    void supports_onlyWebSocket() {
        assertThat(adapter.supports(Channel.WEBSOCKET)).isTrue();
        assertThat(adapter.supports(Channel.IN_APP)).isFalse();
        assertThat(adapter.supports(Channel.WEBHOOK)).isFalse();
        assertThat(adapter.supports(Channel.EMAIL)).isFalse();
    }

    @Test
    void dispatch_withReceiverIds_sendsToUsers() {
        NotificationMessage msg = new NotificationMessage(
                Channel.WEBSOCKET, "标题", "内容", "NOTICE",
                List.of(10L, 20L), null, null, null, Map.of("orderId", 42), null, null);
        when(pushService.sendToUsers(eq(List.of(10L, 20L)), eq(RealtimePushService.TYPE_NOTIFICATION),
                org.mockito.ArgumentMatchers.any(RealtimeNotificationPayload.class))).thenReturn(2);

        int delivered = adapter.dispatch(msg);

        assertThat(delivered).isEqualTo(2);
        verify(pushService).sendToUsers(eq(List.of(10L, 20L)), eq(RealtimePushService.TYPE_NOTIFICATION),
                org.mockito.ArgumentMatchers.any(RealtimeNotificationPayload.class));
        verifyNoMoreInteractions(pushService);
    }

    @Test
    void dispatch_withoutReceiverIds_broadcasts() {
        NotificationMessage msg = new NotificationMessage(
                Channel.WEBSOCKET, "广播标题", "广播内容", "ALERT",
                null, null, null, null, null, null, null);
        when(pushService.broadcast(eq(RealtimePushService.TYPE_NOTIFICATION),
                org.mockito.ArgumentMatchers.any(RealtimeNotificationPayload.class))).thenReturn(7);

        int delivered = adapter.dispatch(msg);

        assertThat(delivered).isEqualTo(7);
        verify(pushService).broadcast(eq(RealtimePushService.TYPE_NOTIFICATION),
                org.mockito.ArgumentMatchers.any(RealtimeNotificationPayload.class));
        verifyNoMoreInteractions(pushService);
    }

    @Test
    void dispatch_payloadCarriesTitleContentAndType() {
        NotificationMessage msg = new NotificationMessage(
                Channel.WEBSOCKET, "T", "C", "SYSTEM", List.of(1L), null, null, null, null, null, null);
        when(pushService.sendToUsers(org.mockito.ArgumentMatchers.anyList(), eq(RealtimePushService.TYPE_NOTIFICATION),
                org.mockito.ArgumentMatchers.any())).thenReturn(1);

        adapter.dispatch(msg);

        org.mockito.ArgumentCaptor<RealtimeNotificationPayload> captor =
                org.mockito.ArgumentCaptor.forClass(RealtimeNotificationPayload.class);
        verify(pushService).sendToUsers(eq(List.of(1L)), eq(RealtimePushService.TYPE_NOTIFICATION), captor.capture());
        RealtimeNotificationPayload payload = captor.getValue();
        assertThat(payload.title()).isEqualTo("T");
        assertThat(payload.content()).isEqualTo("C");
        assertThat(payload.msgType()).isEqualTo("SYSTEM");
        assertThat(payload.extra()).isEmpty();
    }
}
