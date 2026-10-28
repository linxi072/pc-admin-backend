package com.acme.scaffold.common.notification;

/**
 * 通知渠道枚举。框架当前实现：
 * <ul>
 *   <li>{@code IN_APP}（站内信，始终可用）；</li>
 *   <li>{@code WEBHOOK}（通用 Webhook 回调，默认关闭）；</li>
 *   <li>{@code WEBSOCKET}（实时推送，默认关闭）；</li>
 *   <li>{@code EMAIL}（邮件，默认关闭，启用并配置 SMTP 后才发送）；</li>
 *   <li>{@code SMS}（短信，默认关闭；网关经 {@code app.notifications.sms.provider} 选择 LOG/HTTP）；</li>
 *   <li>{@code WECOM}（企业微信机器人，默认关闭，经 Webhook 地址 + secret 签名，复用 {@code webhookUrl} 字段）；</li>
 *   <li>{@code DINGTALK}（钉钉机器人，默认关闭，经 Webhook 地址 + secret 签名，复用 {@code webhookUrl} 字段）。</li>
 * </ul>
 *
 * <p>机器人渠道语义与 {@code WEBHOOK} 对齐：无个人偏好 / 免打扰 / 聚合落库（走各 switch 的 default 分支或显式分支），
 * 仅作为「按地址外发消息」的单向通道。地址统一复用 {@link NotificationMessage#webhookUrl()} 字段，
 * 不额外新增字段。</p>
 */
public enum Channel {
    IN_APP,
    WEBHOOK,
    WEBSOCKET,
    EMAIL,
    SMS,
    WECOM,
    DINGTALK
}
