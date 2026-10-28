package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.notification.config.WecomRobotProperties;
import com.acme.scaffold.system.dto.ConfigVO;
import com.acme.scaffold.system.notification.port.WebhookClient;
import com.acme.scaffold.system.notification.rules.RobotPayloads;
import com.acme.scaffold.system.notification.rules.RobotSigningRules;
import com.acme.scaffold.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 企业微信机器人适配器（M35 / M36 热更新）：将消息作为 {@code text} 类型 POST 到机器人 Webhook 地址。
 *
 * <p>开关（{@code notification.wecom.enabled}）与默认地址（{@code notification.wecom.webhook-url}）
 * 经参数中心（sys_config）读取并热更新：参数中心有值优先于 yml，从而界面调参或直改库后
 * 经 {@code POST /api/system/configs/refresh} 即生效，无需重启。secret 仍走 yml/env（凭据不入参数表）。</p>
 */
@Component
@RequiredArgsConstructor
public class WecomRobotAdapter implements NotificationPort {

    private final WebhookClient webhookClient;
    private final WecomRobotProperties properties;
    private final ConfigService configService;

    private static final String KEY_ENABLED = "notification.wecom.enabled";
    private static final String KEY_WEBHOOK_URL = "notification.wecom.webhook-url";

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.WECOM;
    }

    @Override
    public int dispatch(NotificationMessage message) {
        if (!isEnabled()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "企业微信机器人通知渠道未启用");
        }
        String url = resolveUrl(message);
        String secret = properties.getSecret();
        String target = (secret != null && !secret.isBlank())
                ? RobotSigningRules.buildWecomUrl(url, secret, System.currentTimeMillis())
                : url;
        Map<String, Object> body = RobotPayloads.textPayload(message.title(), message.content());
        webhookClient.post(target, body);
        return 1;
    }

    /** 开关热更新：参数中心优先于 yml 配置。 */
    private boolean isEnabled() {
        String v = configValue(KEY_ENABLED);
        return v != null ? Boolean.parseBoolean(v) : properties.isEnabled();
    }

    private String resolveUrl(NotificationMessage message) {
        String url = (message.webhookUrl() != null && !message.webhookUrl().isBlank())
                ? message.webhookUrl()
                : configValue(KEY_WEBHOOK_URL);
        if (url == null || url.isBlank()) {
            url = properties.getWebhookUrl();
        }
        if (url == null || url.isBlank()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR,
                    "企业微信机器人地址缺失（消息未带、参数中心与配置均无默认）");
        }
        return url;
    }

    private String configValue(String key) {
        ConfigVO vo = configService.getByKey(key);
        return vo != null ? vo.configValue() : null;
    }
}
