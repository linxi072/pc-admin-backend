package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.notification.config.DingtalkRobotProperties;
import com.acme.scaffold.system.notification.port.WebhookClient;
import com.acme.scaffold.system.notification.rules.RobotPayloads;
import com.acme.scaffold.system.notification.rules.RobotSigningRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 钉钉机器人适配器（M35）：将消息作为 {@code text} 类型 POST 到机器人 Webhook 地址。
 *
 * <p>仅当 {@code app.notifications.dingtalk.enabled=true} 且能解析出地址时才外发，
 * 否则抛校验异常，避免通知被静默丢失。地址统一复用 {@link NotificationMessage#webhookUrl()} 字段：
 * 消息自带 url 优先，否则取配置默认地址。secret 配置后按钉钉算法对地址追加
 * {@code timestamp} / {@code sign} 两个 query 参数（base64 再 URL 编码）。</p>
 */
@Component
@RequiredArgsConstructor
public class DingtalkRobotAdapter implements NotificationPort {

    private final WebhookClient webhookClient;
    private final DingtalkRobotProperties properties;

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.DINGTALK;
    }

    @Override
    public int dispatch(NotificationMessage message) {
        if (!properties.isEnabled()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "钉钉机器人通知渠道未启用");
        }
        String url = resolveUrl(message);
        String secret = properties.getSecret();
        String target = (secret != null && !secret.isBlank())
                ? RobotSigningRules.buildDingtalkUrl(url, secret, System.currentTimeMillis())
                : url;
        Map<String, Object> body = RobotPayloads.textPayload(message.title(), message.content());
        webhookClient.post(target, body);
        return 1;
    }

    private String resolveUrl(NotificationMessage message) {
        String url = (message.webhookUrl() != null && !message.webhookUrl().isBlank())
                ? message.webhookUrl()
                : properties.getWebhookUrl();
        if (url == null || url.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "钉钉机器人地址缺失（消息未带且配置无默认）");
        }
        return url;
    }
}
