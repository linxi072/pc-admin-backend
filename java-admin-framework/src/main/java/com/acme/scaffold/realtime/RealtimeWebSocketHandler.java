package com.acme.scaffold.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 实时消息处理器：连接建立后注册到 {@link RealtimeSessionManager}，处理心跳，关闭后注销。
 * <p>鉴权在 {@link RealtimeHandshakeInterceptor#beforeHandshake} 完成，连接建立时已绑定 userId。
 */
@Component
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    public static final String ATTR_USER_ID = "uid";

    private final RealtimeSessionManager sessionManager;

    public RealtimeWebSocketHandler(RealtimeSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        if (userId != null) {
            sessionManager.register(userId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 客户端心跳 ping -> 回应 pong，保持连接
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload) || "\"ping\"".equals(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.unregister(session);
    }
}
