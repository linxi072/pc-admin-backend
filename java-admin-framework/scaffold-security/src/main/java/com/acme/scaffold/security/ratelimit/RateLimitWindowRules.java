package com.acme.scaffold.security.ratelimit;

/**
 * 固定窗口对齐纯规则：集中「窗口长度换算 + 窗口起点对齐」，供本地与 Redis 限流器共用。
 *
 * <p>与 Spring、Redis 完全解耦，便于离线单测。修复前两处实现各自换算窗口：
 * 本地实现用传入的 {@code windowSeconds}，而 Redis 的 {@code currentCount} 写死 60s，
 * 导致非 60s 窗口下观测计数与实际限流口径不一致；本类统一算法后可根除该漂移。</p>
 */
public final class RateLimitWindowRules {

    /** 未记录窗口时的默认窗口秒数（与原 Redis currentCount 行为一致）。 */
    public static final int DEFAULT_WINDOW_SECONDS = 60;

    private RateLimitWindowRules() {
    }

    /**
     * 窗口长度（毫秒）。对非正数窗口归一为 1 秒，避免 {@code % 0} 抛
     * {@link ArithmeticException} 及负数窗口导致的错误对齐。
     */
    public static long windowMillis(int windowSeconds) {
        return Math.max(1, windowSeconds) * 1000L;
    }

    /** 窗口起点：对齐到 {@code windowSeconds} 的整数倍毫秒。 */
    public static long windowStart(long nowMillis, int windowSeconds) {
        long millis = windowMillis(windowSeconds);
        return nowMillis - (nowMillis % millis);
    }
}
