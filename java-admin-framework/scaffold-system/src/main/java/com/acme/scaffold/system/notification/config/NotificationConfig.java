package com.acme.scaffold.system.notification.config;

import com.acme.scaffold.system.notification.unsubscribe.NotificationUnsubscribeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 通知渠道配置：启用 {@link WebhookProperties} 绑定 {@code app.notifications.webhook.*}，
 * {@link EmailProperties} 绑定 {@code app.notifications.email.*}，
 * {@link SmsProperties} 绑定 {@code app.notifications.sms.*}，
 * {@link NotificationUnsubscribeProperties} 绑定 {@code app.notifications.unsubscribe.*}（M15），
 * {@link NotificationRetryProperties} 绑定 {@code app.notifications.retry.*} 与
 * {@link NotificationFailoverProperties} 绑定 {@code app.notifications.failover.*}（M16），
 * {@link NotificationDigestProperties} 绑定 {@code app.notifications.digest.*}（M17）。
 */
@Configuration
@EnableConfigurationProperties({WebhookProperties.class, EmailProperties.class, SmsProperties.class,
        NotificationUnsubscribeProperties.class, NotificationRetryProperties.class,
        NotificationFailoverProperties.class, NotificationDigestProperties.class})
public class NotificationConfig {
}
