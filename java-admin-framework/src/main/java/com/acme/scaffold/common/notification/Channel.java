package com.acme.scaffold.common.notification;

/**
 * 通知渠道枚举。框架当前实现 {@code IN_APP}（站内信）与 {@code WEBHOOK}；
 * {@code EMAIL} / {@code SMS} 为预留位，暂未提供适配器（需对应网关依赖，列入后续增强）。
 */
public enum Channel {
    IN_APP,
    WEBHOOK
}
