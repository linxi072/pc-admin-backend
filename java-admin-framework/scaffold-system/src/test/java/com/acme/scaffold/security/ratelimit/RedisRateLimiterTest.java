package com.acme.scaffold.security.ratelimit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisRateLimiterTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Test
    void withinWindow_allowsUpToMax_thenRejects() {
        InMemoryRateLimiter fallback = new InMemoryRateLimiter();
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, fallback, new RateLimitProperties());

        long now = 1_000_000L;
        int max = 3;
        // 模拟 Redis 原子自增返回 1,2,3,4
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L, 2L, 3L, 4L);

        assertTrue(limiter.tryAcquire("ip:1", max, 60, now));
        assertTrue(limiter.tryAcquire("ip:1", max, 60, now + 1000));
        assertTrue(limiter.tryAcquire("ip:1", max, 60, now + 2000));
        assertFalse(limiter.tryAcquire("ip:1", max, 60, now + 3000));
        verify(redisTemplate, times(4)).execute(any(), anyList(), any());
    }

    @Test
    void newWindowResetsCount() {
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, new InMemoryRateLimiter(), new RateLimitProperties());

        long window = 60_000L;
        long w0 = window; // 对齐到窗口起点
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);
        assertTrue(limiter.tryAcquire("ip:x", 1, 60, w0));
        // 进入下一窗口：新键 → 计数重新从 1 开始
        assertTrue(limiter.tryAcquire("ip:x", 1, 60, w0 + window + 10_000));
    }

    @Test
    void redisUnavailable_failsOpenToLocalFallback() {
        InMemoryRateLimiter fallback = spy(new InMemoryRateLimiter());
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, fallback, new RateLimitProperties());

        when(redisTemplate.execute(any(), anyList(), any()))
                .thenThrow(new RedisSystemException("redis down", new RuntimeException()));

        long now = 5_000_000L;
        int max = 2;
        // 回落本地固定窗口：前 2 次允许，第 3 次拒绝
        assertTrue(limiter.tryAcquire("ip:a", max, 60, now));
        assertTrue(limiter.tryAcquire("ip:a", max, 60, now + 1000));
        assertFalse(limiter.tryAcquire("ip:a", max, 60, now + 2000));
        // 证明确实回落到本地兜底
        verify(fallback, atLeast(3)).tryAcquire(eq("ip:a"), eq(max), eq(60), anyLong());
    }

    @Test
    void redisReturnsNull_failsOpenToLocalFallback() {
        InMemoryRateLimiter fallback = spy(new InMemoryRateLimiter());
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, fallback, new RateLimitProperties());

        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(null);

        assertTrue(limiter.tryAcquire("ip:z", 5, 60, 9_000_000L));
        verify(fallback).tryAcquire(eq("ip:z"), eq(5), eq(60), anyLong());
    }

    @Test
    void keyIncludesPrefixAndAlignedWindowStart() {
        RateLimitProperties props = new RateLimitProperties();
        props.setKeyPrefix("rl");
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, new InMemoryRateLimiter(), props);

        long now = 650_000L; // 窗口起点 = 600000
        when(redisTemplate.execute(any(), anyList(), any())).thenReturn(1L);
        limiter.tryAcquire("ip:k", 5, 60, now);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(), keysCaptor.capture(), any());
        assertEquals("rl:ip:k:600000", keysCaptor.getValue().get(0));
    }
}
