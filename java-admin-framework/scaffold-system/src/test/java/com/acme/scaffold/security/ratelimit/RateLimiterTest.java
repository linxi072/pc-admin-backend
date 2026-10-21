package com.acme.scaffold.security.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimiterTest {

    @Test
    void withinWindow_allowsUpToMax() {
        RateLimiter limiter = new RateLimiter();
        long now = 1_000_000L;
        int max = 3;
        assertTrue(limiter.tryAcquire("ip:1", max, 60, now));
        assertTrue(limiter.tryAcquire("ip:1", max, 60, now + 1000));
        assertTrue(limiter.tryAcquire("ip:1", max, 60, now + 2000));
        // 第 4 次（同窗口）应被拒绝
        assertFalse(limiter.tryAcquire("ip:1", max, 60, now + 3000));
        assertEquals(3, limiter.currentCount("ip:1"));
    }

    @Test
    void differentKeysIndependent() {
        RateLimiter limiter = new RateLimiter();
        long now = 2_000_000L;
        assertTrue(limiter.tryAcquire("ip:a", 1, 60, now));
        assertFalse(limiter.tryAcquire("ip:a", 1, 60, now));
        // 不同 key 不受影响
        assertTrue(limiter.tryAcquire("ip:b", 1, 60, now));
    }

    @Test
    void newWindowResets() {
        RateLimiter limiter = new RateLimiter();
        long window = 60_000L;
        long w0 = window; // 对齐到窗口起点
        assertTrue(limiter.tryAcquire("ip:x", 1, 60, w0));
        assertFalse(limiter.tryAcquire("ip:x", 1, 60, w0 + 10_000));
        // 进入下一个 60s 窗口后计数重置
        assertTrue(limiter.tryAcquire("ip:x", 1, 60, w0 + window + 10_000));
    }
}
