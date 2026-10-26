package com.acme.scaffold.realtime.port;

/**
 * 实时推送模板渲染结果（防腐层返回值）。
 *
 * <p>仅携带实时推送所需的标题 / 正文 / 消息类型，由 realtime 模块据此构造
 * {@link com.acme.scaffold.realtime.payload.RealtimeNotificationPayload}。
 * 字段归一保证非空，调用方无需再做空守卫。</p>
 */
public record RealtimeTemplateRenderResult(String title, String content, String msgType) {

    public RealtimeTemplateRenderResult {
        title = title == null ? "" : title;
        content = content == null ? "" : content;
        msgType = msgType == null || msgType.isBlank() ? "NOTICE" : msgType;
    }
}
