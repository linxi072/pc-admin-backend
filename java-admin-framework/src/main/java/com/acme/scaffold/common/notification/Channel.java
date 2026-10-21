package com.acme.scaffold.common.notification;

/**
 * 通知渠道枚举。框架当前实现 {@code IN_APP}（站内信）、{@code WEBHOOK}、{@code WEBSOCKET}（实时推送）、
 * {@code EMAIL}（邮件，默认关闭，启用并配置 SMTP 后才发送）
 * 与 {@code SMS}（短信，默认关闭；网关经 {@code app.notifications.sms.provider} 选择 LOG/HTTP）。
 */
public enum Channel {
    IN_APP,
    WEBHOOK,
    WEBSOCKET,
    EMAIL,
    SMS
}
