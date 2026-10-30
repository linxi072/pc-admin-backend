package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.notification.config.WebhookProperties;
import com.acme.scaffold.system.notification.port.WebhookClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Webhook 渠道适配器：将消息 POST 到目标地址（消息自带 url 优先，否则取配置默认地址）。
 *
 * <p>仅当 {@code app.notifications.webhook.enabled=true} 且最终能解析出地址时才外发，
 * 否则抛校验异常，避免通知被静默丢失。
 */
@Component
@RequiredArgsConstructor
public class WebhookNotificationAdapter implements NotificationPort {

    private final WebhookClient webhookClient;
    private final WebhookProperties properties;

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.WEBHOOK;
    }

    @Override
    public int dispatch(NotificationMessage message) {
        if (!properties.isEnabled()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "Webhook 通知渠道未启用");
        }
        String url = resolveUrl(message);
        webhookClient.post(url, buildPayload(message));
        return 1;
    }

    private String resolveUrl(NotificationMessage message) {
        String url = (message.webhookUrl() != null && !message.webhookUrl().isBlank())
                ? message.webhookUrl()
                : properties.getDefaultUrl();
        if (url == null || url.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "Webhook 地址缺失（消息未带且配置无默认）");
        }
        return url;
    }

    private Map<String, Object> buildPayload(NotificationMessage message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", message.title());
        body.put("content", message.content());
        body.put("msgType", message.msgType() == null ? "NOTICE" : message.msgType());
        body.put("channel", Channel.WEBHOOK.name());
        body.put("timestamp", System.currentTimeMillis());
        if (message.extra() != null) {
            body.putAll(message.extra());
        }
        if (properties.getSecret() != null && !properties.getSecret().isBlank()) {
            body.put("secret", properties.getSecret());
        }
        return body;
    }
}
