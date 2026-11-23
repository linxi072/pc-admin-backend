package com.acme.scaffold.security.online;

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
 * 心跳节流器单测（缺陷修复回归）：
 * 覆盖节流间隔语义，以及修复「get→put 非原子导致并发重复放行」后的并发契约。
 */
class HeartbeatThrottleTest {

    @Test
    void shouldThrottleWithinIntervalAndAllowAfter() {
        HeartbeatThrottle throttle = new HeartbeatThrottle(5_000L);
        assertTrue(throttle.tryAcquire("u1|d1", 10_000L), "首次放行");
        assertFalse(throttle.tryAcquire("u1|d1", 12_000L), "间隔内拒绝");
        assertTrue(throttle.tryAcquire("u1|d1", 16_000L), "达间隔后放行");
    }

    @Test
    void differentKeysShouldBeIndependent() {
        HeartbeatThrottle throttle = new HeartbeatThrottle(5_000L);
        assertTrue(throttle.tryAcquire("a", 1_000L));
        assertTrue(throttle.tryAcquire("b", 1_000L), "不同用户/设备互不影响");
        assertEquals(2, throttle.size());
    }

    @Test
    void concurrentSameKeyShouldGrantExactlyOnce() throws InterruptedException {
        // 回归缺陷：原实现 get→put 非原子，n 个线程并发可全部通过检查、全部写库。
        // 修复后用 compute 原子化，同一时刻同一 key 只允许一个调用放行。
        HeartbeatThrottle throttle = new HeartbeatThrottle(60_000L);
        int threads = 32;
        long now = 1_700_000_000_000L;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (throttle.tryAcquire("hot-key", now)) {
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
        assertEquals(1, granted.get(), "并发同一 key 必须仅放行一次，杜绝心跳写放大");
    }

    @Test
    void purgeShouldRemoveStaleAndKeepRecent() {
        HeartbeatThrottle throttle = new HeartbeatThrottle(1_000L);
        throttle.tryAcquire("old", 1_000L);
        throttle.tryAcquire("fresh", 5_000L);
        int removed = throttle.purge(5_000L);
        assertEquals(1, removed, "超过 2 个周期的陈旧条目被清理");
        assertEquals(1, throttle.size());
    }
}
