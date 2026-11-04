package com.acme.scaffold.system.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 钉钉机器人渠道配置：{@code app.notifications.dingtalk.*}（M35）。
 *
 * <p>字段与绑定语义见 {@link AbstractRobotProperties}。本类仅声明前缀，并补充渠道专属说明：
 * secret 配置后对外发地址追加 {@code timestamp} / {@code secret} / {@code signature}
 * 三个 query 参数，其中 {@code signature = urlEncode(HmacSHA256(secret, timestamp + "\n" + secret + "\n" + nonce))}；
 * 留空不签名（仅地址直发）。</p>
 */
@ConfigurationProperties(prefix = "app.notifications.dingtalk")
public class DingtalkRobotProperties extends AbstractRobotProperties {
}
