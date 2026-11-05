package com.acme.scaffold.security.ratelimit;

/**
 * fail-closed 兜底限流器（P1-4）。
 *
 * <p>当 Redis 不可用且 {@code app.ratelimit.fail-closed=true} 时作为主限流器返回：
 * 对一切请求均拒绝（{@link #tryAcquire} 恒为 {@code false}，调用方按 429 处理），
 * 避免「限流失效即匿名放开」在分布式部署下被绕过。</p>
 *
 * <p>与 {@link InMemoryRateLimiter}（fail-open 本地计数）语义相反：后者在 Redis 不可用时
 * 退化为实例内独立计数（限流失效但业务不阻断），本类则在同样场景下拒绝服务（可用性让位于安全性）。</p>
 */
public class DenyRateLimiter implements RateLimiter {

    @Override
    public boolean tryAcquire(String key, int max, int windowSeconds) {
        return false;
    }

    @Override
    public boolean tryAcquire(String key, int max, int windowSeconds, long nowMillis) {
        return false;
    }

    @Override
    public int currentCount(String key) {
        return 0;
    }
}
