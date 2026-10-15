package com.acme.scaffold.system.notification;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 通知渠道配置：启用 {@link WebhookProperties} 绑定 {@code app.notifications.webhook.*}。
 */
@Configuration
@EnableConfigurationProperties(WebhookProperties.class)
public class NotificationConfig {
}
