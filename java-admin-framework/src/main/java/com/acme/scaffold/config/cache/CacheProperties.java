package com.acme.scaffold.config.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 缓存管理配置：{@code app.cache.*}。
 *
 * <ul>
 *   <li>{@code ttl}：缓存默认 TTL，{@code @Cacheable} 未显式指定时生效；</li>
 *   <li>{@code key-prefix}：Redis key 前缀，与业务 key 隔离，便于运维排查与批量清理。</li>
 * </ul>
 *
 * <p>缓存为惰性能力：未使用 {@code @Cacheable} 时不会产生任何 Redis 流量；
 * Redis 不可用时仅缓存读写失败，不影响主流程（框架基线假设 Redis 随应用部署）。
 */
@ConfigurationProperties(prefix = "app.cache")
public class CacheProperties {

    /** 默认 TTL：30 分钟。字典等低频变更数据足够，权限类敏感数据不在此列。 */
    private Duration ttl = Duration.ofMinutes(30);

    /** 默认 key 前缀：与 {@code acme:ratelimit:} 等既有前缀保持同族风格。 */
    private String keyPrefix = "acme:cache:";

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }
}
