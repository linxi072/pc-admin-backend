package com.acme.scaffold.realtime.service;

import com.acme.scaffold.realtime.session.RealtimeSessionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link RealtimePushService} 单测。
 *
 * <p>2026-10-19 测试轮次修复：序列化失败时旧实现返回空串仍下发，
 * 客户端收到空负载而 {@code sendToUser} 计数虚高。修复为失败返回 null、放弃发送。</p>
 */
class RealtimePushServiceTest {

    private final RealtimeSessionManager sessionManager = mock(RealtimeSessionManager.class);
    private final RealtimePushService service =
            new RealtimePushService(sessionManager, new ObjectMapper());

    @Test
    @DisplayName("正常 payload：序列化后下发，返回触达会话数")
    void happyPath_delegatesToSessionManager() {
        when(sessionManager.sendToUser(eq(42L), anyString())).thenReturn(2);

        int count = service.sendToUser(42L, RealtimePushService.TYPE_TODO_REMINDER,
                Map.of("title", "待办", "instanceId", "i-1"));

        assertEquals(2, count);
        verify(sessionManager).sendToUser(eq(42L), anyString());
    }

    @Test
    @DisplayName("序列化失败：放弃发送且返回 0（修复前下发空串、计数虚高）")
    void serializationFailure_skipsSendAndReturnsZero() {
        Object poison = new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("getter 抛异常，Jackson 序列化必失败");
            }
        };

        assertEquals(0, service.sendToUser(42L, RealtimePushService.TYPE_NOTIFICATION, poison));
        assertEquals(0, service.sendToUsers(java.util.List.of(1L, 2L),
                RealtimePushService.TYPE_NOTIFICATION, poison));
        assertEquals(0, service.broadcast(RealtimePushService.TYPE_NOTIFICATION, poison));
        verifyNoInteractions(sessionManager);
    }

    @Test
    @DisplayName("广播通道同样遵循序列化失败即放弃")
    void broadcastSerializationFailure_skipsSend() {
        Object poison = new Object() {
            @SuppressWarnings("unused")
            public String getBoom() {
                throw new IllegalStateException("boom");
            }
        };
        assertEquals(0, service.broadcast(RealtimePushService.TYPE_NOTIFICATION, poison));
        verifyNoInteractions(sessionManager);
    }
}
