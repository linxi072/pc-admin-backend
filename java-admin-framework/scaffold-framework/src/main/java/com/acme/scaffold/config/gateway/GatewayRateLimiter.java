package com.acme.scaffold.config.gateway;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关进程内固定窗口限流器（零依赖自研，口径对齐 security 的 InMemoryRateLimiter）。
 *
 * <p>framework 模块不依赖 security（依赖 DAG 白名单约束），故网关层内建轻量实现，
 * 不引入外部依赖。窗口起点对齐到 {@code windowSeconds} 整数倍毫秒，按 key 计数，
 * 超过 max 拒绝且不计数。使用 {@link AtomicInteger} 保证并发自增准确。</p>
 *
 * <p><b>有界性</b>：桶数量由 {@link #purge} 惰性清理（超 {@value #MAX_KEYS} 时移除过期桶），
 * 避免高基数 key（如随机 IP）导致无界增长。接受外部时钟注入，便于离线单测。</p>
 */
public final class GatewayRateLimiter {

    /** 桶数量上限，超出触发惰性清理。 */
    public static final int MAX_KEYS = 10_000;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 尝试获取配额。max<=0 拒绝；窗口非正由 {@link GatewayWindowRules} 归一为 1s。
     */
    public boolean tryAcquire(String key, int max, int windowSeconds) {
        return tryAcquire(key, max, windowSeconds, System.currentTimeMillis());
    }

    /** 受控时钟版本（供测试注入固定时间）。 */
    public boolean tryAcquire(String key, int max, int windowSeconds, long nowMillis) {
        if (key == null || key.isBlank() || max <= 0) {
            return false;
        }
        long windowStart = GatewayWindowRules.windowStart(nowMillis, windowSeconds);
        Bucket bucket = buckets.compute(key, (k, existing) -> {
            if (existing == null || existing.windowStart != windowStart) {
                return new Bucket(windowStart, new AtomicInteger(0));
            }
            return existing;
        });
        AtomicInteger count = bucket.count;
        int current = count.get();
        while (current < max) {
            if (count.compareAndSet(current, current + 1)) {
                maybePurge(nowMillis, windowSeconds);
                return true;
            }
            current = count.get();
        }
        return false;
    }

    /** 当前窗口计数（观测/测试用）。 */
    public int currentCount(String key) {
        Bucket b = buckets.get(key);
        return b == null ? 0 : b.count.get();
    }

    /** 当前跟踪的 key 数。 */
    public int size() {
        return buckets.size();
    }

    private void maybePurge(long nowMillis, int windowSeconds) {
        if (buckets.size() <= MAX_KEYS) {
            return;
        }
        long cutoff = GatewayWindowRules.windowStart(nowMillis, windowSeconds)
                - GatewayWindowRules.windowMillis(windowSeconds);
        buckets.entrySet().removeIf(e -> e.getValue().windowStart <= cutoff);
    }

    private static final class Bucket {
        private final long windowStart;
        private final AtomicInteger count;

        private Bucket(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
