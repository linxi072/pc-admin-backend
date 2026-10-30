package com.acme.scaffold.security.ratelimit;

/**
 * 限流计数抽象：进程内或分布式（Redis）实现共享的统一契约。
 *
 * <p>语义：固定窗口内未超阈值返回 {@code true} 且计数 +1；超阈值返回 {@code false}（不计数）。
 * 窗口起点对齐到 {@code windowSeconds} 的整数倍秒，进入新窗口后计数重置。
 *
 * <p>两种实现：
 * <ul>
 *   <li>{@link InMemoryRateLimiter}：单实例内存计数（CAS 自旋），无外部依赖；</li>
 *   <li>{@link RedisRateLimiter}：多实例共享计数（Redis Lua 原子自增 + TTL），
 *       Redis 不可用时自动 fail-open 回落到 {@link InMemoryRateLimiter}。</li>
 * </ul>
 *
 * <p>{@link #tryAcquire(String, int, int, long)} 接受外部时钟，便于离线单测注入固定时间。
 */
public interface RateLimiter {

    /**
     * 尝试获取一个配额。窗口内未超阈值返回 true 且计数 +1；超阈值返回 false（不计数）。
     * 自动按当前系统时钟判定窗口。
     */
    boolean tryAcquire(String key, int max, int windowSeconds);

    /**
     * 受控时钟版本（供测试注入固定时间）。窗口起点对齐到 {@code windowSeconds} 的整数倍（毫秒）。
     */
    boolean tryAcquire(String key, int max, int windowSeconds, long nowMillis);

    /** 当前某 key 的窗口计数（观测/测试用）。 */
    int currentCount(String key);
}
