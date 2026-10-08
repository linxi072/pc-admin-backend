package com.acme.scaffold.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时会话注册表：维护 userId -> 活跃 WebSocket 会话，支持同一用户多端登录。
 * <p>单例 Bean，全局唯一；连接建立时由 {@link RealtimeWebSocketHandler} 注册，关闭时注销。
 */
@Component
public class RealtimeSessionManager {

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(RealtimeWebSocketHandler.ATTR_USER_ID);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
    }

    /** 向指定用户的所有在线会话推送文本，返回成功发送数。 */
    public int sendToUser(Long userId, String text) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (WebSocketSession s : sessions) {
            if (s.isOpen()) {
                try {
                    s.sendMessage(new TextMessage(text));
                    count++;
                } catch (IOException e) {
                    // 发送失败：会话可能已失效，交由 close 流程清理
                }
            }
        }
        return count;
    }

    /** 向所有在线会话广播文本，返回成功发送数。 */
    public int sendToAll(String text) {
        int count = 0;
        for (Set<WebSocketSession> sessions : userSessions.values()) {
            for (WebSocketSession s : sessions) {
                if (s.isOpen()) {
                    try {
                        s.sendMessage(new TextMessage(text));
                        count++;
                    } catch (IOException e) {
                        // 忽略失效会话
                    }
                }
            }
        }
        return count;
    }

    /** 当前在线会话总数（多端累加）。 */
    public int onlineCount() {
        return userSessions.values().stream().mapToInt(Set::size).sum();
    }
}
