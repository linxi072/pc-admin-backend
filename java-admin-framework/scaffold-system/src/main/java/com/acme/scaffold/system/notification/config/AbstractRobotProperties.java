package com.acme.scaffold.system.notification.config;

/**
 * 机器人渠道配置基类（企业微信 / 钉钉共享）。
 *
 * <p>两渠道配置类（{@link WecomRobotProperties} / {@link DingtalkRobotProperties}）仅
 * {@code @ConfigurationProperties} 前缀不同，绑定字段与语义完全一致，故收敛到本基类：
 * <ul>
 *   <li>{@code enabled}：是否允许外发（默认 false，缺省关闭避免误发）；</li>
 *   <li>{@code webhook-url}：机器人 Webhook 地址（含 key 参数）；消息亦可显式带 {@code webhookUrl} 覆盖此项；</li>
 *   <li>{@code secret}：加签密钥（凭据走 yml/env，不入参数表）；留空不签名。</li>
 * </ul>
 * 子类只需声明各自前缀注解，Spring 仍会对继承字段完成绑定。
 */
public abstract class AbstractRobotProperties {

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
