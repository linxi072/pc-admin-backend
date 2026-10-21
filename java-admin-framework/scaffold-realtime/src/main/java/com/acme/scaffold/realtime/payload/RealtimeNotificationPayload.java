package com.acme.scaffold.realtime.payload;

import java.util.Map;

/**
 * WebSocket 实时通知负载：由 {@code NotificationPort.WEBSOCKET} 适配器构造，
 * 序列化进 {@link RealtimeMessage#payload()}，前端按字段渲染弹窗/红点。
 */
public record RealtimeNotificationPayload(String title, String content, String msgType, Map<String, Object> extra) {

    public static RealtimeNotificationPayload of(String title, String content, String msgType, Map<String, Object> extra) {
        return new RealtimeNotificationPayload(title, content, msgType, extra == null ? Map.of() : extra);
    }
}
