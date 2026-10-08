package com.acme.scaffold.realtime;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 实时推送消息信封。前端按 {@code type} 区分处理：
 * <ul>
 *   <li>UNREAD_MESSAGE —— 收到新站内信，payload 为消息摘要</li>
 *   <li>TODO_REMINDER —— 收到审批待办，payload 为待办摘要</li>
 *   <li>PONG —— 心跳回应</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RealtimeMessage(String type, Object payload, Long ts) {

    public static RealtimeMessage of(String type, Object payload) {
        return new RealtimeMessage(type, payload, System.currentTimeMillis());
    }
}
