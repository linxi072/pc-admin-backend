package com.acme.scaffold.realtime.gateway;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 Spring {@link WebSocketSession} 的 {@link ConnChannel} 适配。
 *
 * <p>将 wg-gateway 的传输无关 {@link GatewayNode} 逻辑接入 Spring WebSocket：
 * <ul>
 *   <li>{@code writeFrame} → {@link BinaryMessage}（复用已迁移的 common.messaging 二进制帧）；</li>
 *   <li>{@code close} → {@link CloseStatus}；</li>
 *   <li>{@code inflight} 近似为「正在发送的帧数」（Spring WS 的 sendMessage 为阻塞式写入，
 *       不暴露底层队列深度，故仅作慢消费者判定的近似信号）。</li>
 * </ul>
 */
public final class SpringConnChannel implements ConnChannel {

    /** 会话属性键：握手成功后写入 userId，便于下行寻路上下文复用。 */
    public static final String ATTR_USER_ID = "uid";

    private final WebSocketSession session;
    private final String connId;
    private final AtomicInteger inflight = new AtomicInteger(0);
    private final AtomicLong dropped = new AtomicLong(0);
    private volatile int lastCloseCode = -1;
    private volatile boolean closed = false;

    public SpringConnChannel(WebSocketSession session) {
        this.session = session;
        this.connId = session.getId();
    }

    @Override
    public String connId() {
        return connId;
    }

    @Override
    public String userId() {
        Object uid = session.getAttributes().get(ATTR_USER_ID);
        return uid == null ? null : String.valueOf(uid);
    }

    @Override
    public void writeFrame(byte[] frame) {
        inflight.incrementAndGet();
        try {
            if (session.isOpen()) {
                session.sendMessage(new BinaryMessage(frame));
            }
        } catch (IOException ex) {
            // 写入失败：交由 close 流程清理（心跳 tick 会回收失效会话）
        } finally {
            inflight.decrementAndGet();
        }
    }

    @Override
    public void close(int code, String reason) {
        if (closed) {
            return;
        }
        closed = true;
        this.lastCloseCode = code;
        if (session.isOpen()) {
            try {
                session.close(new CloseStatus(code, reason));
            } catch (IOException ex) {
                // 关闭失败忽略（资源由容器回收）
            }
        }
    }

    @Override
    public boolean isActive() {
        return session.isOpen() && !closed;
    }

    @Override
    public int inflight() {
        return inflight.get();
    }

    @Override
    public long droppedCount() {
        return dropped.get();
    }

    @Override
    public void markDroppedOldest() {
        dropped.incrementAndGet();
    }

    @Override
    public int closeCode() {
        return lastCloseCode;
    }
}
