package com.acme.scaffold.realtime.gateway;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 连接会话（握手成功后的本节点会话状态）。
 *
 * <p>seq 水位（§3.3/§3.6）：
 * <ul>
 *   <li>{@code nextSeq}：网关分配的下一个下行序号（单调递增）；</li>
 *   <li>{@code lastAckedSeq}：客户端已确认水位（ACK 帧推进），补洞与
 *       重复判定（{@code EnvelopeRules}）的依据。</li>
 * </ul>
 *
 * <p>线程安全：Spring WebSocket 部署下同一会话可被「收帧线程」（onFrame）与
 * 「业务推送线程」（deliver）并发访问，故可变状态一律 volatile / 原子化；
 * seq 分配用 {@link AtomicLong#getAndIncrement} 保证不重不漏，ACK 水位用
 * {@link AtomicLong#updateAndGet} 取 max 保证单调不回绕。
 */
public final class Session {

    private final String connId;
    private final String userId;
    private final long epoch;
    private final long connectedAt;
    private final ConnChannel channel;
    private final RateLimiter limiter;

    private final AtomicLong nextSeq;
    private final AtomicLong ackedSeq;
    private volatile long lastReadAt;
    private volatile long lastPingAt;
    private volatile boolean closed;

    public Session(String connId, String userId, long epoch, long now, ConnChannel channel, RateLimiter limiter) {
        this.connId = connId;
        this.userId = userId;
        this.epoch = epoch;
        this.connectedAt = now;
        this.channel = channel;
        this.limiter = limiter;
        this.lastReadAt = now;
        this.lastPingAt = now;
        this.nextSeq = new AtomicLong(1L);
        this.ackedSeq = new AtomicLong(0L);
    }

    public String connId() {
        return connId;
    }

    public String userId() {
        return userId;
    }

    public long epoch() {
        return epoch;
    }

    public long connectedAt() {
        return connectedAt;
    }

    public ConnChannel channel() {
        return channel;
    }

    public RateLimiter limiter() {
        return limiter;
    }

    public long lastReadAt() {
        return lastReadAt;
    }

    public void markRead(long now) {
        this.lastReadAt = now;
    }

    public long lastPingAt() {
        return lastPingAt;
    }

    public void markPinged(long now) {
        this.lastPingAt = now;
    }

    /** 取下一个下行序号（原子先取后增，多线程推送不重不漏）。 */
    public long nextSeq() {
        return nextSeq.getAndIncrement();
    }

    public long peekNextSeq() {
        return nextSeq.get();
    }

    public long lastAckedSeq() {
        return ackedSeq.get();
    }

    /** ACK 推进水位（原子取 max，单调不退，防乱序 ACK 回绕）。 */
    public void markAcked(long seq) {
        ackedSeq.updateAndGet(current -> Math.max(current, seq));
    }

    public boolean isClosed() {
        return closed;
    }

    public void markClosed() {
        this.closed = true;
    }
}
