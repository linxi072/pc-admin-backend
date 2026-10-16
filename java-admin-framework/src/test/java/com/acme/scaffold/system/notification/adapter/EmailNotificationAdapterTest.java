package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.system.notification.config.EmailProperties;
import com.acme.scaffold.system.notification.port.MailClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link EmailNotificationAdapter} 测试：仅支持 EMAIL；未启用抛校验异常且不外发；
 * 启用后按规则净化并投递，返回收件人数。
 */
class EmailNotificationAdapterTest {

    private final MailClient client = mock(MailClient.class);

    private EmailProperties props(boolean enabled) {
        EmailProperties p = new EmailProperties();
        p.setEnabled(enabled);
        p.setFrom("noreply@example.com");
        return p;
    }

    private EmailNotificationAdapter adapter(boolean enabled) {
        return new EmailNotificationAdapter(client, props(enabled));
    }

    private NotificationMessage email(List<String> emails) {
        return new NotificationMessage(Channel.EMAIL, "系统告警", "CPU 超阈值", "ALERT",
                null, null, null, null, null, emails);
    }

    @Test
    void supportsEmailOnly() {
        EmailNotificationAdapter a = adapter(true);
        assertTrue(a.supports(Channel.EMAIL));
        assertEquals(false, a.supports(Channel.IN_APP));
        assertEquals(false, a.supports(Channel.WEBHOOK));
        assertEquals(false, a.supports(Channel.WEBSOCKET));
    }

    @Test
    void dispatchSendsSanitizedMail() {
        EmailNotificationAdapter a = adapter(true);
        NotificationMessage msg = new NotificationMessage(Channel.EMAIL,
                "告警\r\nBcc: evil@x.com", "内容", "ALERT",
                null, null, null, null, null, List.of(" ops@example.com ", "dev@example.com"));

        assertEquals(2, a.dispatch(msg));
        verify(client).send(eq("noreply@example.com"),
                eq(List.of("ops@example.com", "dev@example.com")),
                eq("告警 Bcc: evil@x.com"), eq("内容"));
    }

    @Test
    void dispatchThrowsWhenDisabled() {
        EmailNotificationAdapter a = adapter(false);
        BusinessException ex = assertThrows(BusinessException.class, () -> a.dispatch(email(List.of("a@x.com"))));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(client, never()).send(anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void dispatchThrowsWhenRecipientsMissing() {
        EmailNotificationAdapter a = adapter(true);
        assertThrows(BusinessException.class, () -> a.dispatch(email(null)));
        assertThrows(BusinessException.class, () -> a.dispatch(email(List.of("  "))));
        verify(client, never()).send(anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void dispatchThrowsWhenRecipientIllegal() {
        EmailNotificationAdapter a = adapter(true);
        assertThrows(BusinessException.class, () -> a.dispatch(email(List.of("ok@x.com", "bad"))));
        verify(client, never()).send(anyString(), anyList(), anyString(), anyString());
    }
}
