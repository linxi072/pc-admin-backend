package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.notification.StepNotifyPort;
import com.acme.scaffold.system.service.MessageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StepNotifyPortAdapter} 单元测试：验证其作为防腐层适配器，仅做「参数透传 + 委托」
 * 给 {@link MessageService#sendCcNotification}，不改变抄送语义（重构批次 B2）。
 */
class StepNotifyPortAdapterTest {

    private final MessageService messageService = mock(MessageService.class);
    private final StepNotifyPort adapter = new StepNotifyPortAdapter(messageService);

    @Test
    void sendCc_delegatesToMessageService_withSameArguments() {
        List<Long> userIds = List.of(11L, 22L, 33L);
        when(messageService.sendCcNotification(userIds, "标题", "正文")).thenReturn(3);

        int sent = adapter.sendCc(userIds, "标题", "正文");

        assertEquals(3, sent);
        verify(messageService).sendCcNotification(userIds, "标题", "正文");
    }

    @Test
    void sendCc_propagatesEmptyReceiverResult() {
        // 空接收人由 MessageService 内部返回 0；适配器忠实透传返回值
        when(messageService.sendCcNotification(anyList(), eq("t"), isNull())).thenReturn(0);

        int sent = adapter.sendCc(List.of(), "t", null);

        assertEquals(0, sent);
    }
}
