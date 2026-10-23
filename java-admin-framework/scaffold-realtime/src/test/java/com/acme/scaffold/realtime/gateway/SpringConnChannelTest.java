package com.acme.scaffold.realtime.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link SpringConnChannel} 适配层单测：帧写入与关闭码映射。 */
@DisplayName("SpringConnChannel Spring 适配")
class SpringConnChannelTest {

    private WebSocketSession mockSession() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("s1");
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());
        return session;
    }

    @Test
    @DisplayName("writeFrame：写入 BinaryMessage 且载荷一致")
    void writeFrame_emitsBinaryMessage() throws Exception {
        WebSocketSession session = mockSession();
        SpringConnChannel ch = new SpringConnChannel(session);
        ch.writeFrame(new byte[]{0x57, 0x01, 0x02});

        var captor = forClass(BinaryMessage.class);
        verify(session).sendMessage(captor.capture());
        assertArrayEquals(new byte[]{0x57, 0x01, 0x02}, captor.getValue().getPayload().array());
        assertEquals("s1", ch.connId());
        assertTrue(ch.isActive());
    }

    @Test
    @DisplayName("close：映射为 CloseStatus 且标记非活跃")
    void close_emitsCloseStatus() throws Exception {
        WebSocketSession session = mockSession();
        SpringConnChannel ch = new SpringConnChannel(session);
        ch.close(4001, "auth failed");

        var captor = forClass(CloseStatus.class);
        verify(session).close(captor.capture());
        assertEquals(4001, captor.getValue().getCode());
        assertEquals("auth failed", captor.getValue().getReason());
        assertFalse(ch.isActive());
        assertEquals(4001, ch.closeCode());
    }

    @Test
    @DisplayName("关闭幂等：重复 close 只生效一次")
    void close_idempotent() throws Exception {
        WebSocketSession session = mockSession();
        SpringConnChannel ch = new SpringConnChannel(session);
        ch.close(1001, "draining");
        ch.close(1001, "draining");
        verify(session, times(1)).close(any(CloseStatus.class));
    }
}
