package com.acme.scaffold.system.notification.unsubscribe;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 退订令牌配置（M15）：{@code app.notifications.unsubscribe.*}。
 *
 * <ul>
 *   <li>{@code enabled}：是否在邮件/短信正文注入退订链接（默认 false，缺省关闭——
 *       不影响既有报文形态，启用后正文才会追加链接）；</li>
 *   <li>{@code secret}：HMAC-SHA256 密钥（必须经环境变量注入，不写入仓库，
 *       与 {@code security.jwt-secret} 的外部化策略一致）；留空时功能视为未激活；</li>
 *   <li>{@code base-url}：退订链接基址（如 {@code https://admin.example.com}），
 *       拼 {@code /api/notification/unsubscribe?token=...}；留空时功能视为未激活；</li>
 *   <li>{@code ttl-hours}：令牌有效期（默认 72 小时——兼顾「稍后决定」与
 *       「链接长期漂浮」的权衡，超期链接不可用）。</li>
 * </ul>
 *
 * <p><b>激活口径</b>（{@link #isActive()}）：三项齐备才实际注入链接。公开退订端点
 * 始终存在（安全白名单放行 GET），但密钥未配置时任何令牌都无法通过验签——
 * 「端点开放、功能关闭」两种状态对调用方都是安全失败。</p>
 */
@ConfigurationProperties(prefix = "app.notifications.unsubscribe")
public class NotificationUnsubscribeProperties {

    private boolean enabled = false;
    private String secret;
    private String baseUrl;
    private long ttlHours = 72;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public long getTtlHours() {
        return ttlHours;
    }

    public void setTtlHours(long ttlHours) {
        this.ttlHours = ttlHours;
    }

    /**
     * 功能是否实际生效：开关打开且密钥、基址齐备。
     * 缺任一即视为未激活（注入与验签均短路），避免半配置状态把半截链接发出去。
     */
    public boolean isActive() {
        return enabled && notBlank(secret) && notBlank(baseUrl);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
