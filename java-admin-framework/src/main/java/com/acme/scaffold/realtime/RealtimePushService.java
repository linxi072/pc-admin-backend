package com.acme.scaffold.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;

/**
 * 实时推送服务：将业务事件（未读消息、待办提醒）按 userId 定向推送到在线 WebSocket 会话。
 * <p>离线用户本次不推送（前端进入页面时通过 REST 拉取全量未读/待办），保证至少一次可见。
 */
@Service
public class RealtimePushService {

    private static final Logger log = LoggerFactory.getLogger(RealtimePushService.class);

    public static final String TYPE_UNREAD_MESSAGE = "UNREAD_MESSAGE";
    public static final String TYPE_TODO_REMINDER = "TODO_REMINDER";
    public static final String TYPE_PONG = "PONG";

    private final RealtimeSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public RealtimePushService(RealtimeSessionManager sessionManager, ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    /** 向单个用户推送，返回成功触达的会话数（0 表示用户离线）。 */
    public int sendToUser(Long userId, String type, Object payload) {
        return sessionManager.sendToUser(userId, toJson(type, payload));
    }

    /** 向多个用户批量推送。 */
    public int sendToUsers(Collection<Long> userIds, String type, Object payload) {
        String json = toJson(type, payload);
        int total = 0;
        for (Long userId : userIds) {
            total += sessionManager.sendToUser(userId, json);
        }
        return total;
    }

    /** 向所有在线会话广播。 */
    public int broadcast(String type, Object payload) {
        return sessionManager.sendToAll(toJson(type, payload));
    }

    private String toJson(String type, Object payload) {
        try {
            return objectMapper.writeValueAsString(RealtimeMessage.of(type, payload));
        } catch (JsonProcessingException e) {
            log.error("实时消息序列化失败 type={}", type, e);
            return "";
        }
    }
}
