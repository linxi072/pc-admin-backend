package com.acme.scaffold.system.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业微信机器人渠道配置：{@code app.notifications.wecom.*}（M35）。
 *
 * <ul>
 *   <li>{@code enabled}：是否允许外发（默认 false，缺省关闭避免误发）；</li>
 *   <li>{@code webhook-url}：机器人 Webhook 地址（含 {@code key} 参数）；
 *       消息亦可显式带 {@code webhookUrl} 覆盖此项；</li>
 *   <li>{@code secret}：加签密钥。配置后对外发报文按企业微信算法
 *       ({@code base64(HmacSHA256(secret, timestamp + "\\n" + secret + "\\n" + nonce))})
 *       签名并注入 {@code X-Wechat-Signature} 头；留空不签名。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.notifications.wecom")
public class WecomRobotProperties {

    private boolean enabled = false;
    private String webhookUrl;
    private String secret;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
