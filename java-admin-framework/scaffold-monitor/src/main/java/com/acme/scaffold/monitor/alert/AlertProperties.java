package com.acme.scaffold.monitor.alert;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 告警接收配置。前缀 {@code monitoring.alert}。
 */
@ConfigurationProperties(prefix = "monitoring.alert")
public class AlertProperties {

    private final Webhook webhook = new Webhook();
    /** 告警规则周期评估配置。 */
    private final Rule rule = new Rule();
    /** 内存收件箱最大保留条数（环形裁剪，避免无限增长）。默认 200。 */
    private int inboxCapacity = 200;

    public Webhook getWebhook() {
        return webhook;
    }

    public Rule getRule() {
        return rule;
    }

    public int getInboxCapacity() {
        return inboxCapacity;
    }

    public void setInboxCapacity(int inboxCapacity) {
        this.inboxCapacity = inboxCapacity;
    }

    /**
     * 告警规则闭环配置：是否启用周期评估、评估 Cron 表达式、可选出站 Webhook 地址。
     *
     * <p>默认 {@code enabled=false}：仅当显式开启时才由 {@code AlertRuleScheduler} 拉起
     * Spring {@code @Scheduled} 评估任务，避免无规则时空转。webhookUrl 留空表示命中规则
     * 仅入收件箱、不出站转发。</p>
     */
    public static class Rule {
        /** 是否启用告警规则周期评估。默认 false。 */
        private boolean enabled = false;
        /** 评估周期 Cron 表达式（Spring @Scheduled 语义，默认每分钟一次）。 */
        private String cron = "0 */1 * * * *";
        /** 命中规则后的可选出站 Webhook 地址；留空则仅写入收件箱。 */
        private String webhookUrl = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }
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
