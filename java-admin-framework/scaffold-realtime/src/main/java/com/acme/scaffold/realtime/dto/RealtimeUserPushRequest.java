package com.acme.scaffold.realtime.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 向指定用户集合定向推送实时消息的请求。
 * <p>{@code type} 复用 RealtimePushService 定义的类型常量；
 * {@code userIds} 为空时由 Bean Validation 在控制器层拦截（返回 {@code COMMON_001}）。</p>
 *
 * <p><b>M30 模板化</b>：可选携带 {@code templateCode} + {@code variables} 复用通知模板中心
 * （WEBSOCKET 渠道）渲染实时通知负载，减少手填 payload；此时 {@code type} 由服务端按
 * {@code NOTIFICATION} 处理、{@code payload} 被忽略。</p>
 */
public record RealtimeUserPushRequest(
        @NotEmpty(message = "{validation.realtime.userIds.notEmpty}") List<Long> userIds,
        @NotBlank(message = "{validation.realtime.type.notBlank}") String type,
        Object payload,
        String templateCode,
        Map<String, String> variables) {
}
