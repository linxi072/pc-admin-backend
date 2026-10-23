package com.acme.scaffold.realtime.gateway;

/**
 * 单连接令牌桶限流器（对应设计稿 §3.2：100 msg/s、突发 500，超限 4028）。
 *
 * <p>惰性补充（lazy refill）：不启用定时器，每次取令牌时按时间差结算，
 * 空闲连接零成本。与 Kafka 侧 quota 互补：这是源头削峰。
 */
public final class RateLimiter {

    private final double perSecond;
    private final double burst;
    private final Clock clock;

    private double tokens;
    private long lastRefillAt;

    /**
     * @param perSecond 稳态速率（令牌/秒）
     * @param burst     突发容量（令牌桶深度）
     */
    public RateLimiter(double perSecond, double burst, Clock clock) {
        if (perSecond <= 0 || burst <= 0) {
            throw new IllegalArgumentException("速率与突发必须为正: " + perSecond + "/" + burst);
        }
        if (burst < perSecond) {
            throw new IllegalArgumentException("突发容量不宜小于每秒速率（无法吸收正常抖动）");
        }
        this.perSecond = perSecond;
        this.burst = burst;
        this.clock = clock;
        this.tokens = burst;
        this.lastRefillAt = clock.nowMillis();
    }

    /** 尝试取 1 个令牌。 */
    public boolean tryConsume() {
        return tryConsume(1);
    }

    /** 尝试取 n 个令牌；不足则拒绝（不部分放行，保持语义简单）。 */
    public synchronized boolean tryConsume(int permits) {
        if (permits <= 0) {
            throw new IllegalArgumentException("permits 必须为正: " + permits);
        }
        refill(clock.nowMillis());
        if (tokens >= permits) {
            tokens -= permits;
            return true;
        }
        return false;
    }

    /** 当前可用令牌（调试/测试）。 */
    public synchronized double availableTokens() {
        refill(clock.nowMillis());
        return tokens;
    }

    /** 重置为满桶（连接重连时调用，避免旧状态拖累新会话）。 */
    public synchronized void reset() {
        this.tokens = burst;
        this.lastRefillAt = clock.nowMillis();
    }

    private void refill(long now) {
        if (now <= lastRefillAt) {
            return;
        }
        double elapsedSeconds = (now - lastRefillAt) / 1000.0;
        tokens = Math.min(burst, tokens + elapsedSeconds * perSecond);
        lastRefillAt = now;
    }
}
