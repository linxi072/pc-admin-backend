package com.acme.scaffold.system.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮件渠道配置：{@code app.notifications.email.*}。
 *
 * <ul>
 *   <li>{@code enabled}：是否允许发送（默认 false，缺省关闭避免误发与启动依赖 SMTP）；</li>
 *   <li>{@code from}：发件人地址（启用后必配）；</li>
 *   <li>{@code host} / {@code port}：SMTP 服务器地址与端口（25 / 465 / 587 依服务商）；</li>
 *   <li>{@code username} / {@code password}：凭据；服务商允许匿名转发时可留空；</li>
 *   <li>{@code starttls}：是否开启 STARTTLS 升级（587 通常为 true，465 走 SMTPS 时由端口推断）；</li>
 *   <li>{@code timeout-ms}：读写超时。</li>
 * </ul>
 *
 * <p>生产环境凭据必须经环境变量注入（{@code NOTIFICATION_EMAIL_PASSWORD} 等），
 * 不写入仓库，与 {@code security.jwt-secret} 的外部化策略一致。
 */
@ConfigurationProperties(prefix = "app.notifications.email")
public class EmailProperties {

    private boolean enabled = false;
    private String from;
    private String host = "localhost";
    private int port = 25;
    private String username;
    private String password;
    private boolean starttls = false;
    private int timeoutMs = 5000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isStarttls() {
        return starttls;
    }

    public void setStarttls(boolean starttls) {
        this.starttls = starttls;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
}
