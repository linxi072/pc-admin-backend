package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.system.dto.SendMessageRequest;
import com.acme.scaffold.system.service.MessageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InAppNotificationAdapter} 测试：仅声明支持 IN_APP，且将消息原样转交 {@link MessageService#doSend}。
 */
class InAppNotificationAdapterTest {

    private final MessageService messageService = mock(MessageService.class);
    private final InAppNotificationAdapter adapter = new InAppNotificationAdapter(messageService);

    @Test
    void supportsInAppOnly() {
        assertEquals(true, adapter.supports(Channel.IN_APP));
        assertEquals(false, adapter.supports(Channel.WEBHOOK));
        assertEquals(false, adapter.supports(Channel.EMAIL));
    }

    @Test
    void dispatchDelegatesToMessageService() {
        NotificationMessage msg = new NotificationMessage(Channel.IN_APP, "t", "c", "NOTICE",
                List.of(1L, 2L), null, null, null, null, null);
        when(messageService.doSend(any(SendMessageRequest.class))).thenReturn(2);

        int n = adapter.dispatch(msg);

        assertEquals(2, n);
        verify(messageService).doSend(any(SendMessageRequest.class));
    }

    @Test
    void dispatchPassesNullMsgTypeThrough() {
        NotificationMessage msg = new NotificationMessage(Channel.IN_APP, "t", "c", null,
                null, List.of(10L), null, null, null, null);
        when(messageService.doSend(any(SendMessageRequest.class))).thenReturn(1);

        assertEquals(1, adapter.dispatch(msg));
        verify(messageService).doSend(any(SendMessageRequest.class));
    }
}
