package com.acme.scaffold.security.ratelimit;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存固定窗口限流器：按 key（如客户端 IP）统计窗口内的请求数，超过阈值拒绝。
 *
 * <p>特点：
 * <ul>
 *   <li>固定窗口（窗口起点对齐到 epoch 秒），实现简单、无锁竞争（CAS 自增）；</li>
 *   <li>与 Spring 解耦，核心 {@link #tryAcquire(String, int, int, long)} 接受外部时钟，便于离线单测；</li>
 *   <li>单实例有效；多实例部署应由 {@link RedisRateLimiter} 共享计数（保持 key 维度一致）。</li>
 * </ul>
 *
 * <p>作为分布式限流器的本地兜底：Redis 不可用时 {@link RedisRateLimiter} 回落到本实现，
 * 保证限流本身不成为可用性瓶颈（代价是多实例下计数不共享）。
 *
 * <p>本类不标注 {@code @Component}：其 Bean 由 {@link RateLimitConfig#inMemoryRateLimiter()} 显式注册，
 * 避免在组件扫描下与 {@code @Bean} 定义产生重复 Bean 定义。
 */
public class InMemoryRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 尝试获取一个配额。窗口内未超阈值返回 true 且计数 +1；超阈值返回 false（不计数）。
     * 自动按当前系统时钟判定窗口。
     */
    @Override
    public boolean tryAcquire(String key, int max, int windowSeconds) {
        return tryAcquire(key, max, windowSeconds, System.currentTimeMillis());
    }

    /**
     * 受控时钟版本（供测试注入固定时间）。窗口起点对齐到 {@code windowSeconds} 的整数倍（毫秒）。
     */
    @Override
    public boolean tryAcquire(String key, int max, int windowSeconds, long nowMillis) {
        long windowMillis = (long) windowSeconds * 1000L;
        long windowStart = nowMillis - (nowMillis % windowMillis);
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(windowStart, 0));
        if (bucket.windowStart != windowStart) {
            // 进入新窗口：重置计数（即便并发也仅首个进入新窗口的线程重置，其余走旧桶的 CAS 重试）
            Bucket fresh = new Bucket(windowStart, 0);
            bucket = buckets.compute(key, (k, old) ->
                    (old != null && old.windowStart == windowStart) ? old : fresh);
        }
        // 自旋 CAS 自增，超过阈值则回退
        for (int attempt = 0; attempt < 3; attempt++) {
            Bucket cur = buckets.get(key);
            if (cur == null || cur.windowStart != windowStart) {
                cur = buckets.compute(key, (k, old) ->
                        (old != null && old.windowStart == windowStart) ? old : new Bucket(windowStart, 0));
            }
            if (cur.count >= max) {
                return false;
            }
            Bucket updated = new Bucket(windowStart, cur.count + 1);
            if (buckets.replace(key, cur, updated)) {
                return true;
            }
        }
        return false;
    }

    /** 当前某 key 的窗口计数（观测/测试用）。 */
    @Override
    public int currentCount(String key) {
        Bucket b = buckets.get(key);
        return b == null ? 0 : b.count;
    }

    private record Bucket(long windowStart, int count) {
    }
}
