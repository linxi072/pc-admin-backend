package com.acme.scaffold.realtime.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 向所有在线会话广播实时消息的请求。
 * <p>{@code type} 复用 RealtimePushService 定义的类型常量
 * （UNREAD_MESSAGE / TODO_REMINDER / NOTIFICATION / PONG）。
 * {@code payload} 透传业务负载，可为 {@code null}（仅推类型通知时）。</p>
 *
 * <p><b>M30 模板化</b>：可选携带 {@code templateCode} + {@code variables} 复用通知模板中心
 * （WEBSOCKET 渠道）渲染实时通知负载，减少手填 payload；此时 {@code type} 由服务端按
 * {@code NOTIFICATION} 处理、{@code payload} 被忽略。二者皆为空时走原始 {@code type}/{@code payload} 路径。</p>
 */
public record RealtimeBroadcastRequest(
        @NotBlank(message = "{validation.realtime.type.notBlank}") String type,
        Object payload,
        String templateCode,
        Map<String, String> variables) {
}
