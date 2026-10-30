package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.system.notification.config.WecomRobotProperties;
import com.acme.scaffold.system.notification.port.WebhookClient;
import com.acme.scaffold.system.notification.rules.RobotSigningRules;
import com.acme.scaffold.system.service.ConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link WecomRobotAdapter} 测试：支持 WECOM；消息自带 {@code webhookUrl} 优先于配置默认地址；
 * 未启用或地址缺失时抛校验异常且不外发；配置 secret 时按企业微信算法对地址追加
 * {@code timestamp} / 原始 base64 {@code sign} 两个 query 参数。
 */
class WecomRobotAdapterTest {

    private final WebhookClient client = mock(WebhookClient.class);
    private final ConfigService configService = mock(ConfigService.class);

    private WecomRobotProperties props(boolean enabled, String url, String secret) {
        WecomRobotProperties p = new WecomRobotProperties();
        p.setEnabled(enabled);
        p.setWebhookUrl(url);
        p.setSecret(secret);
        return p;
    }

    private WecomRobotAdapter adapter(WecomRobotProperties p) {
        return new WecomRobotAdapter(client, p, configService);
    }

    @Test
    void supportsWecomOnly() {
        WecomRobotAdapter a = adapter(props(true, "https://x", null));
        assertEquals(true, a.supports(Channel.WECOM));
        assertEquals(false, a.supports(Channel.IN_APP));
    }

    @Test
    void dispatchPostsToMessageUrlWhenNoSecret() {
        WecomRobotAdapter a = adapter(props(true, "https://default", null));
        NotificationMessage msg = new NotificationMessage(Channel.WECOM, "t", "c", null,
                null, null, null, "https://msg", null, null, null);
        assertEquals(1, a.dispatch(msg));
        verify(client).post(eq("https://msg"), any(Map.class));
    }

    @Test
    void dispatchSignsWhenSecretConfigured() {
        String secret = "sec123";
        WecomRobotAdapter a = adapter(props(true, "https://default", secret));
        NotificationMessage msg = new NotificationMessage(Channel.WECOM, "t", "c", null,
                null, null, null, "https://msg", null, null, null);
        a.dispatch(msg);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(client).post(urlCaptor.capture(), any(Map.class));
        String url = urlCaptor.getValue();
        long ts = Long.parseLong(extractParam(url, "timestamp"));
        String sign = extractParam(url, "sign");
        assertEquals(RobotSigningRules.signWecom(secret, ts), sign);
    }

    @Test
    void dispatchFallsBackToDefaultUrl() {
        WecomRobotAdapter a = adapter(props(true, "https://default", null));
        NotificationMessage msg = new NotificationMessage(Channel.WECOM, "t", "c", null,
                null, null, null, null, null, null, null);
        a.dispatch(msg);
        verify(client).post(eq("https://default"), any(Map.class));
    }

    @Test
    void throwsWhenDisabled() {
        WecomRobotAdapter a = adapter(props(false, "https://x", null));
        NotificationMessage msg = new NotificationMessage(Channel.WECOM, "t", "c", null,
                null, null, null, "https://x", null, null, null);
        BusinessException ex = assertThrows(BusinessException.class, () -> a.dispatch(msg));
        assertEquals(CommonErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(client, never()).post(any(), any());
    }

    @Test
    void throwsWhenUrlMissing() {
        WecomRobotAdapter a = adapter(props(true, null, null));
        NotificationMessage msg = new NotificationMessage(Channel.WECOM, "t", "c", null,
                null, null, null, null, null, null, null);
        assertThrows(BusinessException.class, () -> a.dispatch(msg));
        verify(client, never()).post(any(), any());
    }

    private static String extractParam(String url, String key) {
        int idx = url.indexOf(key + "=");
        if (idx < 0) {
            throw new AssertionError("missing query param: " + key);
        }
        int start = idx + key.length() + 1;
        int end = url.indexOf('&', start);
        return end < 0 ? url.substring(start) : url.substring(start, end);
    }
}
