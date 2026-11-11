package com.acme.scaffold.system.notification.adapter;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.notification.Channel;
import com.acme.scaffold.common.notification.NotificationMessage;
import com.acme.scaffold.common.notification.NotificationPort;
import com.acme.scaffold.system.config.application.ConfigService;
import com.acme.scaffold.system.config.dto.ConfigVO;
import com.acme.scaffold.system.notification.config.AbstractRobotProperties;
import com.acme.scaffold.system.notification.domain.repository.WebhookClient;
import com.acme.scaffold.system.notification.domain.rule.RobotPayloads;

import java.util.Map;

/**
 * 机器人适配器基类（企业微信 / 钉钉共享）：将消息作为 {@code text} 类型 POST 到机器人 Webhook 地址。
 *
 * <p>两适配器流程完全一致：① 渠道开关校验（参数中心优先于 yml）→ ② 解析地址
 * （消息自带 → 参数中心 → 配置默认）→ ③ secret 存在时按平台算法对地址加签 → ④ 构造 text 报文并外发。
 * 差异仅在于：支持渠道、参数中心键、未启用报错文案、加签算法。这些差异经构造参数与
 * {@link #buildSignedUrl(String, String, long)} 下放到子类，基类不再重复。</p>
 *
 * <p>开关与默认地址经参数中心（sys_config）热更新：参数中心有值优先于 yml，界面调参或直改库后
 * 经 {@code POST /api/system/configs/refresh} 即生效，无需重启。secret 仍走 yml/env（凭据不入参数表）。</p>
 */
public abstract class AbstractRobotAdapter<P extends AbstractRobotProperties> implements NotificationPort {

    private final WebhookClient webhookClient;
    private final P properties;
    private final ConfigService configService;
    private final Channel supportedChannel;
    private final String enabledKey;
    private final String webhookUrlKey;
    private final String disabledMessage;
    private final String missingUrlMessage;

    protected AbstractRobotAdapter(WebhookClient webhookClient, P properties, ConfigService configService,
                                   Channel supportedChannel, String enabledKey, String webhookUrlKey,
                                   String disabledMessage, String missingUrlMessage) {
        this.webhookClient = webhookClient;
        this.properties = properties;
        this.configService = configService;
        this.supportedChannel = supportedChannel;
        this.enabledKey = enabledKey;
        this.webhookUrlKey = webhookUrlKey;
        this.disabledMessage = disabledMessage;
        this.missingUrlMessage = missingUrlMessage;
    }

    @Override
    public boolean supports(Channel channel) {
        return channel == supportedChannel;
    }

    @Override
    public int dispatch(NotificationMessage message) {
        if (!isEnabled()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, disabledMessage);
        }
        String url = resolveUrl(message);
        String secret = properties.getSecret();
        String target = (secret != null && !secret.isBlank())
                ? buildSignedUrl(url, secret, System.currentTimeMillis())
                : url;
        Map<String, Object> body = RobotPayloads.textPayload(message.title(), message.content());
        webhookClient.post(target, body);
        return 1;
    }

    /** 平台特定加签：在 {@code baseUrl} 上追加时间戳与签名 query 参数，返回最终外发地址。 */
    protected abstract String buildSignedUrl(String baseUrl, String secret, long timestamp);

    /** 开关热更新：参数中心优先于 yml 配置。 */
    private boolean isEnabled() {
        String v = configValue(enabledKey);
        return v != null ? Boolean.parseBoolean(v) : properties.isEnabled();
    }

    private String resolveUrl(NotificationMessage message) {
        return RobotUrlSupport.resolveUrl(message, configValue(webhookUrlKey), properties.getWebhookUrl(),
                missingUrlMessage);
    }

    private String configValue(String key) {
        ConfigVO vo = configService.getByKey(key);
        return vo != null ? vo.configValue() : null;
    }
}
