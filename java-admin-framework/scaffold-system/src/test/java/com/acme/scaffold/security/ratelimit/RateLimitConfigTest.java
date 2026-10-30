package com.acme.scaffold.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 限流装配默认分支（{@code app.ratelimit.store=redis}）验证：
 * 主 {@link RateLimiter} 应为 {@link RedisRateLimiter}，且本地兜底 {@link InMemoryRateLimiter} 已注册。
 * 不依赖真实 Redis（注入 mock 的 {@link StringRedisTemplate}），离线可跑。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {RateLimitConfig.class, RateLimitConfigTest.RedisConfig.class})
class RateLimitConfigTest {

    @Configuration
    static class RedisConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private ApplicationContext context;

    @Test
    void defaultStore_redis_primaryIsRedisRateLimiter() {
        assertThat(rateLimiter).isInstanceOf(RedisRateLimiter.class);
    }

    @Test
    void inMemoryFallbackBeanRegistered() {
        assertThat(context.getBean(InMemoryRateLimiter.class)).isNotNull();
    }
}
