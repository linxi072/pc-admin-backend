package com.acme.scaffold.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 限流装配 LOCAL 分支（{@code app.ratelimit.store=local}）验证：
 * 主 {@link RateLimiter} 直接为 {@link InMemoryRateLimiter}，不引入 Redis 依赖。
 */
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = "app.ratelimit.store=local")
@ContextConfiguration(classes = {RateLimitConfig.class, RateLimitConfigLocalTest.RedisConfig.class})
class RateLimitConfigLocalTest {

    @Configuration
    static class RedisConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return mock(StringRedisTemplate.class);
        }
    }

    @Autowired
    private RateLimiter rateLimiter;

    @Test
    void localStore_primaryIsInMemoryRateLimiter() {
        assertThat(rateLimiter).isInstanceOf(InMemoryRateLimiter.class);
    }
}
