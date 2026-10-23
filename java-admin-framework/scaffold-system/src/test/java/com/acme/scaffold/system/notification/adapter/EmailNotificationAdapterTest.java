package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.system.notification.config.EmailProperties;
import com.acme.scaffold.system.notification.port.MailClient;
import com.acme.scaffold.system.notification.unsubscribe.NotificationUnsubscribeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link EmailNotificationAdapter} 测试：仅支持 EMAIL；未启用抛校验异常且不外发；
 * 启用后按规则净化并投递，返回收件人数；M15 退订链接激活后逐收件人个性化发送。
 */
@DisplayName("邮件适配器：净化投递与 M15 退订链接注入")
class EmailNotificationAdapterTest {

    private final MailClient client = mock(MailClient.class);
    private final NotificationUnsubscribeService unsubscribeService =
            mock(NotificationUnsubscribeService.class);

    private EmailProperties props(boolean enabled) {
        EmailProperties p = new EmailProperties();
        p.setEnabled(enabled);
        p.setFrom("noreply@example.com");
        return p;
    }

    private EmailNotificationAdapter adapter(boolean enabled) {
        return new EmailNotificationAdapter(client, props(enabled), unsubscribeService);
    }

    private NotificationMessage email(List<String> emails) {
        return new NotificationMessage(Channel.EMAIL, "系统告警", "CPU 超阈值", "ALERT",
                null, null, null, null, null, emails, null);
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
                null, null, null, null, null, List.of(" ops@example.com ", "dev@example.com"), null);

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

    @Test
    @DisplayName("M15：退订激活且有可解析收件人时逐封个性化，正文尾部追加各自链接")
    void dispatchInjectsUnsubscribeLinkPerRecipient() {
        EmailNotificationAdapter a = adapter(true);
        when(unsubscribeService.resolveLinks(anyList(), eq(true), eq(Channel.EMAIL), eq("ALERT")))
                .thenReturn(Map.of("ops@example.com", "https://admin.example.com/api/notification/unsubscribe?token=t-ops",
                        "dev@example.com", "https://admin.example.com/api/notification/unsubscribe?token=t-dev"));
        NotificationMessage msg = email(List.of("ops@example.com", "dev@example.com"));

        assertEquals(2, a.dispatch(msg));
        // 逐收件人单封发送，每人拿到自己的退订链接（外部地址大小写不影响键匹配：内部已小写归一）
        verify(client).send(eq("noreply@example.com"), eq(List.of("ops@example.com")),
                eq("系统告警"), eq("CPU 超阈值\n\n如不想再收到此类邮件，可点击以下链接退订："
                        + "https://admin.example.com/api/notification/unsubscribe?token=t-ops"));
        verify(client).send(eq("noreply@example.com"), eq(List.of("dev@example.com")),
                eq("系统告警"), eq("CPU 超阈值\n\n如不想再收到此类邮件，可点击以下链接退订："
                        + "https://admin.example.com/api/notification/unsubscribe?token=t-dev"));
    }

    @Test
    @DisplayName("M15：仅部分收件人可解析时，可解析者带链接、其余原正文，仍逐封发送")
    void dispatchInjectsLinkOnlyForResolvedRecipients() {
        EmailNotificationAdapter a = adapter(true);
        when(unsubscribeService.resolveLinks(anyList(), eq(true), eq(Channel.EMAIL), eq("ALERT")))
                .thenReturn(Map.of("ops@example.com",
                        "https://admin.example.com/api/notification/unsubscribe?token=t-ops"));
        NotificationMessage msg = email(List.of("ops@example.com", "external@vendor.com"));

        assertEquals(2, a.dispatch(msg));
        verify(client).send(eq("noreply@example.com"), eq(List.of("ops@example.com")),
                eq("系统告警"), eq("CPU 超阈值\n\n如不想再收到此类邮件，可点击以下链接退订："
                        + "https://admin.example.com/api/notification/unsubscribe?token=t-ops"));
        // 外部收件人无链接：原正文单封发送
        verify(client).send(eq("noreply@example.com"), eq(List.of("external@vendor.com")),
                eq("系统告警"), eq("CPU 超阈值"));
    }

    @Test
    @DisplayName("M15：链接键大小写不敏感匹配（反查键为小写归一地址）")
    void dispatchMatchesLinkKeyCaseInsensitively() {
        EmailNotificationAdapter a = adapter(true);
        when(unsubscribeService.resolveLinks(anyList(), eq(true), eq(Channel.EMAIL), eq("ALERT")))
                .thenReturn(Map.of("ops@example.com",
                        "https://admin.example.com/api/notification/unsubscribe?token=t-ops"));
        NotificationMessage msg = email(List.of("Ops@Example.com"));

        assertEquals(1, a.dispatch(msg));
        verify(client).send(eq("noreply@example.com"), eq(List.of("Ops@Example.com")),
                eq("系统告警"), eq("CPU 超阈值\n\n如不想再收到此类邮件，可点击以下链接退订："
                        + "https://admin.example.com/api/notification/unsubscribe?token=t-ops"));
    }
}
