package com.acme.scaffold.system.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 企业微信机器人渠道配置：{@code app.notifications.wecom.*}（M35）。
 *
 * <p>字段与绑定语义见 {@link AbstractRobotProperties}。本类仅声明前缀，并补充渠道专属说明：
 * secret 配置后对外发报文按企业微信算法
 * ({@code base64(HmacSHA256(secret, timestamp + "\n" + secret + "\n" + nonce))})
 * 签名并注入 {@code X-Wechat-Signature} 头；留空不签名。</p>
 */
@ConfigurationProperties(prefix = "app.notifications.wecom")
public class WecomRobotProperties extends AbstractRobotProperties {
}
