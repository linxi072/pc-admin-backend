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

    /**
     * 回归测试：复现「首页登录异常」根因（StringRedisTemplate 的 args 序列化器为
     * StringRedisSerializer，只接受 String）。脚本参数（窗口毫秒数）必须以 String 形式传入，
     * 否则 DefaultScriptExecutor 用 StringRedisSerializer 序列化 Long 会抛出
     * ClassCastException: java.lang.Long cannot be cast to java.lang.String。
     *
     * <p>该用例通过 Answer 拦截实际传入 execute 的实参并断言其类型，在修复前会因
     * 参数为 Long 而失败，修复后（String.valueOf(windowMillis)）通过。
     */
    @Test
    void scriptArgsMustBeString_forStringRedisTemplate() {
        InMemoryRateLimiter fallback = new InMemoryRateLimiter();
        RedisRateLimiter limiter = new RedisRateLimiter(redisTemplate, fallback, new RateLimitProperties());

        when(redisTemplate.execute(any(), anyList(), any())).thenAnswer(invocation -> {
            Object[] allArgs = invocation.getArguments();
            // 签名：execute(RedisScript, List<K> keys, Object... args) -> varargs 展开为独立实参
            // allArgs[0]=script, allArgs[1]=keys, allArgs[2..]=脚本 ARGV
            assertTrue(allArgs.length >= 3, "应至少传入 1 个脚本参数（窗口毫秒数）");
            for (int i = 2; i < allArgs.length; i++) {
                assertTrue(allArgs[i] instanceof String,
                        "StringRedisTemplate 的脚本参数必须是 String，但第 " + (i - 1)
                                + " 个参数为 " + (allArgs[i] == null ? "null" : allArgs[i].getClass()));
            }
            return 1L;
        });

        // 故意使用 windowSeconds 使 windowMillis 为较大 Long，回归真正的类型场景
        assertTrue(limiter.tryAcquire("ip:arg", 3, 60, 1_000_000L));
        verify(redisTemplate, times(1)).execute(any(), anyList(), any());
    }
}
