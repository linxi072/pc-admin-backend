package com.acme.scaffold.realtime.gateway;

/**
 * 连接会话（握手成功后的本节点会话状态）。
 *
 * <p>seq 水位（§3.3/§3.6）：
 * <ul>
 *   <li>{@code nextSeq}：网关分配的下一个下行序号（单调递增）；</li>
 *   <li>{@code lastAckedSeq}：客户端已确认水位（ACK 帧推进），补洞与
 *       重复判定（{@code EnvelopeRules}）的依据。</li>
 * </ul>
 */
public final class Session {

    private final String connId;
    private final String userId;
    private final long epoch;
    private final long connectedAt;
    private final ConnChannel channel;
    private final RateLimiter limiter;

    private long lastReadAt;
    private long lastPingAt;
    private long nextSeq;
    private long lastAckedSeq;
    private boolean closed;

    public Session(String connId, String userId, long epoch, long now, ConnChannel channel, RateLimiter limiter) {
        this.connId = connId;
        this.userId = userId;
        this.epoch = epoch;
        this.connectedAt = now;
        this.channel = channel;
        this.limiter = limiter;
        this.lastReadAt = now;
        this.lastPingAt = now;
        this.nextSeq = 1L;
        this.lastAckedSeq = 0L;
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

    /** 取下一个下行序号（先取后增）。 */
    public long nextSeq() {
        return nextSeq++;
    }

    public long peekNextSeq() {
        return nextSeq;
    }

    public long lastAckedSeq() {
        return lastAckedSeq;
    }

    /** ACK 推进水位（单调不退，防乱序 ACK 回绕）。 */
    public void markAcked(long seq) {
        this.lastAckedSeq = Math.max(this.lastAckedSeq, seq);
    }

    public boolean isClosed() {
        return closed;
    }

    public void markClosed() {
        this.closed = true;
    }
}
