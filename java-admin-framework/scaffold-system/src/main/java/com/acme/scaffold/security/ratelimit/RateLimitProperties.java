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
