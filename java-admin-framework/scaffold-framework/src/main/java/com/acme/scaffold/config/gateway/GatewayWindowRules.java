package com.acme.scaffold.config.gateway;

/**
 * 网关固定窗口对齐纯规则（零依赖，framework 内自足）。
 *
 * <p>与 security 模块的 {@code RateLimitWindowRules} 口径一致，但因依赖 DAG 约束
 * （framework 不依赖 security）在此独立实现，保证网关层零外部依赖。</p>
 */
public final class GatewayWindowRules {

    private GatewayWindowRules() {
    }

    /** 窗口长度（毫秒），非正数归一为 1 秒，避免取模除零。 */
    public static long windowMillis(int windowSeconds) {
        return Math.max(1, windowSeconds) * 1000L;
    }

    /** 窗口起点：对齐到窗口长度整数倍毫秒。 */
    public static long windowStart(long nowMillis, int windowSeconds) {
        long millis = windowMillis(windowSeconds);
        return nowMillis - (nowMillis % millis);
    }
}
