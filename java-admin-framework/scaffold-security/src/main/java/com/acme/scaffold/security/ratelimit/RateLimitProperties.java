package com.acme.scaffold.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 限流相关配置（绑定 {@code app.ratelimit.*}）。
 *
 * <p>默认 {@code store=redis}：走 Redis 共享计数；Redis 不可用时由
 * {@link RedisRateLimiter} 自动 fail-open 回落到进程内计数。
 * 若明确希望单实例或彻底不依赖 Redis，可设 {@code app.ratelimit.store=local} 直接走本地计数。
 */
@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    /** 限流计数存储后端。 */
    private Store store = Store.REDIS;

    /** Redis 键前缀，用于隔离限流计数与其它键空间。 */
    private String keyPrefix = "ratelimit";

    /**
     * Redis 不可用时是否 fail-closed（拒绝服务）而非 fail-open 回落本地计数。
     * 默认 false（兼容旧语义：Redis 不可用时限流失效但不阻断业务）；
     * 设为 true 后，Redis 不可用（或计数异常）将拒绝全部请求（429），
     * 适用于「限流失效比限流误杀更不可接受」的高安全场景（如防爆破）。
     */
    private boolean failClosed = false;

    public boolean isFailClosed() {
        return failClosed;
    }

    public void setFailClosed(boolean failClosed) {
        this.failClosed = failClosed;
    }

    public enum Store {
        /** 进程内内存计数（单实例 / 不依赖 Redis）。 */
        LOCAL,
        /** Redis 共享计数（多实例），不可用时 fail-open 回落本地。 */
        REDIS
    }

    public Store getStore() {
        return store;
    }

    public void setStore(Store store) {
        this.store = store;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
