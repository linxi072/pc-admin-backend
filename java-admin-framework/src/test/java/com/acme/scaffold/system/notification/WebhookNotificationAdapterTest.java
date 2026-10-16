package com.acme.scaffold.system.notification;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link WebhookNotificationAdapter} 测试：支持 WEBHOOK；消息自带 url 优先于配置默认地址；
 * 未启用或地址缺失时抛校验异常且不外发。
 */
class WebhookNotificationAdapterTest {

    private final WebhookClient client = mock(WebhookClient.class);

    private WebhookProperties props(boolean enabled, String defaultUrl) {
        WebhookProperties p = new WebhookProperties();
        p.setEnabled(enabled);
        p.setDefaultUrl(defaultUrl);
        p.setSecret("s3cr3t");
        return p;
    }

    private WebhookNotificationAdapter adapter() {
        return new WebhookNotificationAdapter(client, props(true, "https://hook.example.com/default"));
    }

    @Test
    void supportsWebhookOnly() {
        WebhookNotificationAdapter a = adapter();
        assertEquals(true, a.supports(Channel.WEBHOOK));
        assertEquals(false, a.supports(Channel.IN_APP));
    }

    @Test
    void dispatchPostsToMessageUrlWhenProvided() {
        WebhookNotificationAdapter a = adapter();
        NotificationMessage msg = new NotificationMessage(Channel.WEBHOOK, "t", "c", "ALERT",
                null, null, null, "https://hook.example.com/custom", Map.of("orderId", 42), null);

        assertEquals(1, a.dispatch(msg));
        verify(client).post(eq("https://hook.example.com/custom"), any(Map.class));
    }

    @Test
    void dispatchFallsBackToDefaultUrl() {
        WebhookNotificationAdapter a = adapter();
        NotificationMessage msg = new NotificationMessage(Channel.WEBHOOK, "t", "c", null,
                null, null, null, null, null, null);

        a.dispatch(msg);
        verify(client).post(eq("https://hook.example.com/default"), any(Map.class));
    }

    @Test
    void throwsWhenDisabled() {
        WebhookNotificationAdapter a = new WebhookNotificationAdapter(client, props(false, "https://x"));
        NotificationMessage msg = new NotificationMessage(Channel.WEBHOOK, "t", "c", null,
                null, null, null, "https://x", null, null);

        BusinessException ex = assertThrows(BusinessException.class, () -> a.dispatch(msg));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(client, never()).post(any(), any());
    }

    @Test
    void throwsWhenUrlMissing() {
        WebhookNotificationAdapter a = new WebhookNotificationAdapter(client, props(true, null));
        NotificationMessage msg = new NotificationMessage(Channel.WEBHOOK, "t", "c", null,
                null, null, null, null, null, null);

        assertThrows(BusinessException.class, () -> a.dispatch(msg));
        verify(client, never()).post(any(), any());
    }
}
