package com.acme.scaffold.security.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 限流组件装配：按 {@link RateLimitProperties} 选择计数后端。
 *
 * <p>默认 {@code app.ratelimit.store=redis}：装配 {@link RedisRateLimiter}（注入
 * Spring Boot 提供的 {@link StringRedisTemplate}，并以其为 fail-open 兜底）。
 * 当显式设为 {@code local}，或运行环境无 {@link StringRedisTemplate} 时，
 * 直接使用 {@link InMemoryRateLimiter}。
 *
 * <p>无论哪种后端，统一以 {@link RateLimiter} 接口暴露给 {@link RateLimitAspect}
 * 与 {@link RateLimitFilter}，调用方无感知。
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private final RateLimitProperties properties;

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
    }

    /** 本地兜底限流器（始终注册，供 Redis 后端 fail-open 复用）。 */
    @Bean
    public InMemoryRateLimiter inMemoryRateLimiter() {
        return new InMemoryRateLimiter();
    }

    /**
     * 主限流器（{@code @Primary}）：根据配置与 Redis 可用性选择 Redis 或本地实现。
     */
    @Bean
    @Primary
    public RateLimiter rateLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                   InMemoryRateLimiter fallback) {
        if (properties.getStore() == RateLimitProperties.Store.LOCAL) {
            log.info("限流后端=LOCAL（进程内内存计数）");
            return fallback;
        }
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("未检测到 StringRedisTemplate，限流回落进程内内存计数");
            return fallback;
        }
        log.info("限流后端=REDIS（多实例共享计数，不可用时 fail-open 回落本地）");
        return new RedisRateLimiter(redisTemplate, fallback, properties);
    }
}
