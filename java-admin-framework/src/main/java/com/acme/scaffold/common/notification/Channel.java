package com.acme.scaffold.common.notification;

/**
 * 通知渠道枚举。框架当前实现 {@code IN_APP}（站内信）、{@code WEBHOOK}、{@code WEBSOCKET}（实时推送）
 * 与 {@code EMAIL}（邮件，默认关闭，启用并配置 SMTP 后才发送）；
 * {@code SMS} 为预留位，暂未提供适配器（需短信网关依赖，列入后续增强）。
 */
public enum Channel {
    IN_APP,
    WEBHOOK,
    WEBSOCKET,
    EMAIL
}
