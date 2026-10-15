package com.acme.scaffold.system.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Webhook 渠道配置：{@code app.notifications.webhook.*}。
 *
 * <ul>
 *   <li>{@code enabled}：是否允许外发（默认 false，缺省关闭避免误发）；</li>
 *   <li>{@code default-url}：消息未显式带 url 时的兜底地址；</li>
 *   <li>{@code secret}：可选共享密钥，透传为报文 {@code secret} 字段；</li>
 *   <li>{@code timeout-ms} / {@code connect-timeout-ms}：读写 / 连接超时。</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.notifications.webhook")
public class WebhookProperties {

    private boolean enabled = false;
    private String defaultUrl;
    private String secret;
    private int timeoutMs = 5000;
    private int connectTimeoutMs = 2000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultUrl() {
        return defaultUrl;
    }

    public void setDefaultUrl(String defaultUrl) {
        this.defaultUrl = defaultUrl;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }
}
