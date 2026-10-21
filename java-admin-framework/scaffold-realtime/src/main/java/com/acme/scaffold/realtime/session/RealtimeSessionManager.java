package com.acme.scaffold.realtime.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时会话注册表：维护 userId -> 活跃 WebSocket 会话，支持同一用户多端登录。
 * <p>单例 Bean，全局唯一；连接建立时由
 * {@link com.acme.scaffold.realtime.websocket.RealtimeWebSocketHandler} 注册，关闭时注销。
 */
@Component
public class RealtimeSessionManager {

    /** 会话属性中 userId 的键：握手鉴权通过后由 {@code RealtimeHandshakeInterceptor} 写入。 */
    public static final String ATTR_USER_ID = "uid";

    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void unregister(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        if (userId == null) {
            return;
        }
        // 与 register 的 computeIfAbsent 互斥成交替原子：杜绝「删旧会话」的 get→remove→remove(key)
        // 与「加新会话」的 computeIfAbsent→add 交错时，把刚注册的新会话连同空 Set 一并删掉
        // （该新会话将永不收到推送且永远无法被清理）
        userSessions.computeIfPresent(userId, (k, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
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

    /** 在线用户 -> 活跃会话数的只读映射（多端累加），供管理端展示在线会话分布。 */
    public Map<Long, Integer> onlineUserCounts() {
        Map<Long, Integer> result = new HashMap<>(userSessions.size());
        userSessions.forEach((userId, sessions) -> result.put(userId, sessions.size()));
        return result;
    }
}
