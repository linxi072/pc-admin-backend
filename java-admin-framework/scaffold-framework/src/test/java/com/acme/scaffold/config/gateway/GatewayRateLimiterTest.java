package com.acme.scaffold.config.gateway;

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
 * 网关进程内限流器单测（方向 B）：固定窗口计数、窗口重置、key 隔离、边界与并发安全。
 */
class GatewayRateLimiterTest {

    @Test
    void fixedWindowShouldCountAndReset() {
        GatewayRateLimiter limiter = new GatewayRateLimiter();
        long t0 = 1_700_000_000_000L;
        assertTrue(limiter.tryAcquire("ip1", 2, 60, t0));
        assertTrue(limiter.tryAcquire("ip1", 2, 60, t0 + 1000));
        assertFalse(limiter.tryAcquire("ip1", 2, 60, t0 + 2000), "窗口内第 3 次拒绝");
        assertEquals(2, limiter.currentCount("ip1"));

        long nextWindow = t0 + 60_000L;
        assertTrue(limiter.tryAcquire("ip1", 2, 60, nextWindow), "新窗口重置");
        assertEquals(1, limiter.currentCount("ip1"));
    }

    @Test
    void differentKeysShouldBeIndependent() {
        GatewayRateLimiter limiter = new GatewayRateLimiter();
        assertTrue(limiter.tryAcquire("ip1", 1, 60, 1000L));
        assertFalse(limiter.tryAcquire("ip1", 1, 60, 1000L));
        assertTrue(limiter.tryAcquire("ip2", 1, 60, 1000L));
    }

    @Test
    void invalidInputsShouldBeRejected() {
        GatewayRateLimiter limiter = new GatewayRateLimiter();
        assertFalse(limiter.tryAcquire(null, 5, 60, 1000L));
        assertFalse(limiter.tryAcquire("  ", 5, 60, 1000L));
        assertFalse(limiter.tryAcquire("ip", 0, 60, 1000L));
        assertFalse(limiter.tryAcquire("ip", -1, 60, 1000L));
    }

    @Test
    void nonPositiveWindowShouldNormalize() {
        GatewayRateLimiter limiter = new GatewayRateLimiter();
        // windowSeconds<=0 归一为 1s，不应抛 ArithmeticException
        assertTrue(limiter.tryAcquire("ip", 1, 0, 1234L));
        assertFalse(limiter.tryAcquire("ip", 1, -5, 1500L), "同一 1s 窗口内第二次拒绝");
        assertTrue(limiter.tryAcquire("ip", 1, 0, 2500L), "下一秒窗口放行");
    }

    @Test
    void concurrentAcquireShouldNotExceedMax() throws InterruptedException {
        GatewayRateLimiter limiter = new GatewayRateLimiter();
        int max = 20;
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (limiter.tryAcquire("hot", max, 60, 1000L)) {
                        granted.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(max, granted.get(), "并发下恰好放行 max 次");
    }
}
