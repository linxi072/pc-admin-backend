package com.acme.scaffold.monitor.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 告警接收配置。前缀 {@code monitoring.alert}。
 */
@ConfigurationProperties(prefix = "monitoring.alert")
public class AlertProperties {

    private final Webhook webhook = new Webhook();
    /** 内存收件箱最大保留条数（环形裁剪，避免无限增长）。默认 200。 */
    private int inboxCapacity = 200;

    public Webhook getWebhook() {
        return webhook;
    }

    public int getInboxCapacity() {
        return inboxCapacity;
    }

    public void setInboxCapacity(int inboxCapacity) {
        this.inboxCapacity = inboxCapacity;
    }

    public static class Webhook {
        /** 是否启用应用内告警接收端。默认 true。 */
        private boolean enabled = true;
        /** 非空时，要求请求携带匹配的 {@code X-Alert-Token} 头；生产须由网络/网关层加固。 */
        private String token = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }
    }
}
