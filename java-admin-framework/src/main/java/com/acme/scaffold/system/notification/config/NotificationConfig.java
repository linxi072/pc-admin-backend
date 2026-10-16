package com.acme.scaffold.system.notification.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 通知渠道配置：启用 {@link WebhookProperties} 绑定 {@code app.notifications.webhook.*}，
 * {@link EmailProperties} 绑定 {@code app.notifications.email.*}。
 */
@Configuration
@EnableConfigurationProperties({WebhookProperties.class, EmailProperties.class})
public class NotificationConfig {
}
