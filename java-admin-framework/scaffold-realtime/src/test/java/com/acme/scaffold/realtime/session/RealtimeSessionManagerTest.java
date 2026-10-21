package com.acme.scaffold.realtime.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RealtimeSessionManager} 单测。
 *
 * <p>2026-10-19 测试轮次修复：注销的 {@code get→remove→isEmpty→remove(key)} 与注册的
 * {@code computeIfAbsent→add} 非原子，并发交错时新会话会被连同空 Set 一起删掉
 * （永不收到推送且无法清理）。修复为 {@code computeIfPresent} 后本组用例全绿。</p>
 */
class RealtimeSessionManagerTest {

    private static WebSocketSession session(Long userId) {
        WebSocketSession s = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(RealtimeSessionManager.ATTR_USER_ID, userId);
        when(s.getAttributes()).thenReturn(attrs);
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    @Test
    @DisplayName("注销一个会话不影响同一用户的其他会话")
    void unregisterKeepsOtherSessions() {
        RealtimeSessionManager manager = new RealtimeSessionManager();
        WebSocketSession s1 = session(1L);
        WebSocketSession s2 = session(1L);
        manager.register(1L, s1);
        manager.register(1L, s2);

        manager.unregister(s1);

        assertEquals(1, manager.onlineCount());
        assertEquals(1, manager.sendToUser(1L, "payload"));
    }

    @Test
    @DisplayName("注销最后一个会话后清理整条映射（sendToUser 返回 0）")
    void unregisterLastSessionCleansEntry() {
        RealtimeSessionManager manager = new RealtimeSessionManager();
        WebSocketSession s1 = session(1L);
        manager.register(1L, s1);

        manager.unregister(s1);

        assertEquals(0, manager.onlineCount());
        assertEquals(0, manager.sendToUser(1L, "payload"));
    }

    @Test
    @DisplayName("并发注册/注销不丢失新会话（修复前交错时会话被孤立）")
    void concurrentRegisterUnregister_keepsNewSession() throws Exception {
        RealtimeSessionManager manager = new RealtimeSessionManager();
        WebSocketSession oldSession = session(1L);
        WebSocketSession newSession = session(1L);
        manager.register(1L, oldSession);

        for (int i = 0; i < 500; i++) {
            final CountDownLatch start = new CountDownLatch(1);
            Thread register = new Thread(() -> {
                await(start);
                manager.register(1L, newSession);
            });
            Thread unregister = new Thread(() -> {
                await(start);
                manager.unregister(oldSession);
            });
            register.start();
            unregister.start();
            start.countDown();
            register.join(2000);
            unregister.join(2000);

            // 不变量：旧会话已注销、新会话已注册后，推送必须能触达新会话
            assertEquals(1, manager.sendToUser(1L, "payload"),
                    "第 " + i + " 轮：新会话被孤立（注册与注销的非原子交错）");
            manager.unregister(newSession);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
