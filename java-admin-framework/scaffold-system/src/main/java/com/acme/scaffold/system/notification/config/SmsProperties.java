package com.acme.scaffold.system.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 短信渠道配置：{@code app.notifications.sms.*}。
 *
 * <ul>
 *   <li>{@code enabled}：是否允许发送（默认 false，缺省关闭避免误发与费用）；</li>
 *   <li>{@code provider}：网关实现选择——{@code LOG}（默认，仅打日志不真发，开发/联调用）
 *       或 {@code HTTP}（通用 HTTP 网关，POST JSON）；</li>
 *   <li>{@code gateway-url}：HTTP 模式必配，短信网关接收地址（YAML 含冒号须加引号）；</li>
 *   <li>{@code app-id} / {@code app-key}：网关凭据（{@code app-key} 经环境变量注入，不落库）；</li>
 *   <li>{@code signature}：短信签名（如 {@code 【Acme】}），拼接到正文头部；</li>
 *   <li>{@code timeout-ms} / {@code connect-timeout-ms}：HTTP 读写/连接超时。</li>
 * </ul>
 *
 * <p>网关报文为厂商无关的最小契约（{@code appId}/{@code appKey}/{@code mobiles}/{@code content}），
 * 对接具体厂商（阿里云/腾讯云等）时在网关前加适配或用其 SDK 替换
 * {@code HttpSmsGateway} 实现即可，渠道编排与规则层不变。
 */
@ConfigurationProperties(prefix = "app.notifications.sms")
public class SmsProperties {

    /** 网关实现选择。 */
    public enum Provider {
        /** 开发默认：仅记录脱敏日志，不真实发送。 */
        LOG,
        /** 通用 HTTP 网关：POST JSON 到 {@code gateway-url}。 */
        HTTP
    }

    private boolean enabled = false;
    private Provider provider = Provider.LOG;
    private String gatewayUrl;
    private String appId;
    private String appKey;
    private String signature;
    private int timeoutMs = 5000;
    private int connectTimeoutMs = 2000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Provider getProvider() {
        return provider;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
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
