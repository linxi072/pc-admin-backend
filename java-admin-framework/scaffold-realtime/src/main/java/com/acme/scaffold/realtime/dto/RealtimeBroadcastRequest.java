package com.acme.scaffold.realtime.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 向所有在线会话广播实时消息的请求。
 * <p>{@code type} 复用 RealtimePushService 定义的类型常量
 * （UNREAD_MESSAGE / TODO_REMINDER / NOTIFICATION / PONG）。
 * {@code payload} 透传业务负载，可为 {@code null}（仅推类型通知时）。
 */
public record RealtimeBroadcastRequest(
        @NotBlank(message = "{validation.realtime.type.notBlank}") String type,
        Object payload) {
}
