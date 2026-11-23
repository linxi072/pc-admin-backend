package com.acme.scaffold.security.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 限流器单测（TEST-COVERAGE-PLAN 第二批 / T1）：
 * InMemoryRateLimiter 固定窗口计数 + DenyRateLimiter fail-closed 语义。
 */
class RateLimiterTest {

    @Test
    void inMemoryShouldCountWithinWindowAndRejectBeyondMax() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        long now = 1_700_000_000_000L;
        // max=3, window=60s
        assertTrue(limiter.tryAcquire("ip1", 3, 60, now));
        assertTrue(limiter.tryAcquire("ip1", 3, 60, now + 1000));
        assertTrue(limiter.tryAcquire("ip1", 3, 60, now + 2000));
        assertFalse(limiter.tryAcquire("ip1", 3, 60, now + 3000), "第 4 次请求被拒绝");
        assertEquals(3, limiter.currentCount("ip1"));
        assertEquals(0, limiter.currentCount("ip2"), "不同 key 独立计数");
    }

    @Test
    void inMemoryShouldResetOnNewWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        long now = 1_700_000_000_000L;
        assertTrue(limiter.tryAcquire("k", 1, 10, now));
        assertFalse(limiter.tryAcquire("k", 1, 10, now + 1000), "同窗口内第二次拒绝");
        // 窗口起点对齐到 windowSeconds 整数倍：进入下一个 10s 窗口
        long nextWindow = now - (now % 10_000L) + 10_000L;
        assertTrue(limiter.tryAcquire("k", 1, 10, nextWindow), "新窗口计数重置");
        assertEquals(1, limiter.currentCount("k"));
    }

    @Test
    void inMemoryShouldRejectZeroOrNegativeMax() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        assertFalse(limiter.tryAcquire("k", 0, 60, 1000L));
        assertFalse(limiter.tryAcquire("k", -1, 60, 1000L));
    }

    @Test
    void inMemoryConcurrentAcquireShouldNotExceedMax() throws InterruptedException {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter();
        long now = 1_700_000_000_000L;
        int max = 20;
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (limiter.tryAcquire("concurrent", max, 60, now)) granted.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(max, granted.get(), "并发下放行数量不得超过阈值");
    }

    @Test
    void denyLimiterShouldAlwaysReject() {
        DenyRateLimiter limiter = new DenyRateLimiter();
        assertFalse(limiter.tryAcquire("any", 1000, 60));
        assertFalse(limiter.tryAcquire("any", 1000, 60, 12345L));
        assertEquals(0, limiter.currentCount("any"));
    }
}
