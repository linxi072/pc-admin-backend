package com.acme.scaffold.security.online;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeartbeatThrottleTest {

    private static final long INTERVAL = 60_000L;

    @Test
    void firstRequestAlwaysAcquires() {
        HeartbeatThrottle throttle = new HeartbeatThrottle(INTERVAL);
        assertTrue(throttle.tryAcquire("u1|d1", 0L));
    }

    @Test
    void secondRequestWithinIntervalSkipped() {
        HeartbeatThrottle throttle = new HeartbeatThrottle(INTERVAL);
        assertTrue(throttle.tryAcquire("u1|d1", 0L));
        assertFalse(throttle.tryAcquire("u1|d1", 59_999L));
    }

    @Test
    void requestAfterIntervalAcquires() {
        HeartbeatThrottle throttle = new HeartbeatThrottle(INTERVAL);
        assertTrue(throttle.tryAcquire("u1|d1", 0L));
        assertFalse(throttle.tryAcquire("u1|d1", 59_999L));
        assertTrue(throttle.tryAcquire("u1|d1", 60_000L));
    }

    @Test
    void keysAreIsolatedPerUserAndDevice() {
        // 不同用户 / 不同设备互不影响
        HeartbeatThrottle throttle = new HeartbeatThrottle(INTERVAL);
        assertTrue(throttle.tryAcquire("u1|d1", 0L));
        assertTrue(throttle.tryAcquire("u1|d2", 0L));
        assertTrue(throttle.tryAcquire("u2|d1", 0L));
        assertFalse(throttle.tryAcquire("u1|d1", 1_000L));
        assertTrue(throttle.tryAcquire("u2|d1", 60_000L));
    }

    @Test
    void tinyIntervalIsClampedToAtLeastOneSecond() {
        // 构造参数 < 1s 时按 1s 兜底，防止误配置导致写放大
        HeartbeatThrottle throttle = new HeartbeatThrottle(100L);
        assertTrue(throttle.tryAcquire("u1|d1", 0L));
        assertFalse(throttle.tryAcquire("u1|d1", 999L));
        assertTrue(throttle.tryAcquire("u1|d1", 1_000L));
    }

    @Test
    void purgeRemovesStaleEntriesAndKeepsFreshOnes() {
        HeartbeatThrottle throttle = new HeartbeatThrottle(INTERVAL);
        // 构造两个周期前的陈旧条目 + 刚写入的新条目
        throttle.tryAcquire("stale", 0L);
        throttle.tryAcquire("fresh", 120_001L);
        assertEquals(2, throttle.size());
        assertEquals(1, throttle.purge(120_001L));
        assertEquals(1, throttle.size());
    }

    @Test
    void purgeKeepsEntriesAtExactlyTwoCycles() {
        // 边界：恰好 2 个周期（>= 2*interval）视为陈旧
        HeartbeatThrottle throttle = new HeartbeatThrottle(INTERVAL);
        throttle.tryAcquire("edge", 0L);
        assertEquals(1, throttle.purge(120_000L));
        assertEquals(0, throttle.size());
    }

    @Test
    void purgeKeepsEntriesJustUnderTwoCycles() {
        HeartbeatThrottle throttle = new HeartbeatThrottle(INTERVAL);
        throttle.tryAcquire("edge", 0L);
        assertEquals(0, throttle.purge(119_999L));
        assertEquals(1, throttle.size());
    }

    @Test
    void autoPurgeWhenEntriesExceedMax() {
        // 超过 MAX_ENTRIES 触发自动清理：只保留近 2 个周期内活跃的 key
        HeartbeatThrottle throttle = new HeartbeatThrottle(INTERVAL);
        int overflow = HeartbeatThrottle.MAX_ENTRIES + 5;
        for (int i = 0; i < overflow; i++) {
            // 全部写入同一时刻：都不是陈旧条目，自动清理后数量维持在 overflow
            throttle.tryAcquire("k" + i, 0L);
        }
        assertEquals(overflow, throttle.size());
        // 换到 3 个周期后：全部陈旧，再写一个即触发清理回落到 1
        throttle.tryAcquire("brand-new", 180_001L);
        assertEquals(1, throttle.size());
    }
}
