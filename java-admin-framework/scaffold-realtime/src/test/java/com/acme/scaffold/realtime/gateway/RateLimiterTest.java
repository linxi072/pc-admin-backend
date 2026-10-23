package com.acme.scaffold.realtime.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link RateLimiter} 令牌桶单测（惰性补充）。 */
@DisplayName("RateLimiter 令牌桶")
class RateLimiterTest {

    @Test
    @DisplayName("初始为满桶，突发容量内全部放行")
    void burst_allows() {
        MutableClock clock = new MutableClock(0L);
        RateLimiter limiter = new RateLimiter(100.0, 500.0, clock);
        for (int i = 0; i < 500; i++) {
            assertTrue(limiter.tryConsume(), "第 " + i + " 次应放行（突发内）");
        }
        assertFalse(limiter.tryConsume(), "超过突发容量应拒绝");
    }

    @Test
    @DisplayName("时间推进后补充令牌")
    void refill_replenishes() {
        MutableClock clock = new MutableClock(0L);
        RateLimiter limiter = new RateLimiter(100.0, 500.0, clock);
        for (int i = 0; i < 500; i++) {
            limiter.tryConsume();
        }
        assertFalse(limiter.tryConsume());

        clock.advanceBy(1000L); // +1s → 约 100 令牌
        for (int i = 0; i < 100; i++) {
            assertTrue(limiter.tryConsume(), "补充后第 " + i + " 次应放行");
        }
        assertFalse(limiter.tryConsume());
    }
}
