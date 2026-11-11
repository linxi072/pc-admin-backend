package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.system.config.application.ConfigService;
import com.acme.scaffold.system.notification.config.DingtalkRobotProperties;
import com.acme.scaffold.system.notification.domain.repository.WebhookClient;
import com.acme.scaffold.system.notification.domain.rule.RobotSigningRules;
import org.springframework.stereotype.Component;

/**
 * 钉钉机器人适配器（M35 / M36 热更新）：将消息作为 {@code text} 类型 POST 到机器人 Webhook 地址。
 *
 * <p>开关（{@code notification.dingtalk.enabled}）与默认地址（{@code notification.dingtalk.webhook-url}）
 * 经参数中心（sys_config）读取并热更新：参数中心有值优先于 yml，从而界面调参或直改库后
 * 经 {@code POST /api/system/configs/refresh} 即生效，无需重启。secret 仍走 yml/env（凭据不入参数表）。</p>
 *
 * <p>加签：secret 配置后对外发地址追加 {@code timestamp} / URL 编码的 {@code sign} 两个 query 参数
 * （{@code sign = urlEncode(base64(HmacSHA256(secret, timestamp + "\n" + secret)))}），详见 {@link RobotSigningRules}。</p>
 */
@Component
public class DingtalkRobotAdapter extends AbstractRobotAdapter<DingtalkRobotProperties> {

    private static final String KEY_ENABLED = "notification.dingtalk.enabled";
    private static final String KEY_WEBHOOK_URL = "notification.dingtalk.webhook-url";

    public DingtalkRobotAdapter(WebhookClient webhookClient, DingtalkRobotProperties properties, ConfigService configService) {
        super(webhookClient, properties, configService, Channel.DINGTALK, KEY_ENABLED, KEY_WEBHOOK_URL,
                "钉钉机器人通知渠道未启用",
                "钉钉机器人地址缺失（消息未带、参数中心与配置均无默认）");
    }

    @Override
    protected String buildSignedUrl(String baseUrl, String secret, long timestamp) {
        return RobotSigningRules.buildDingtalkUrl(baseUrl, secret, timestamp);
    }
}
