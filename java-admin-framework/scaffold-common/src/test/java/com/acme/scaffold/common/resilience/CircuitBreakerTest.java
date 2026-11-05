package com.acme.scaffold.common.resilience;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    @Test
    void closedAllowsThenTripsOpenAfterThreshold() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        // 连续 3 次失败 => OPEN
        for (int i = 0; i < 3; i++) {
            try {
                cb.execute(() -> {
                    throw new RuntimeException("down");
                });
            } catch (RuntimeException ignored) {
            }
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void openRejectsFastWithException() {
        CircuitBreaker cb = new CircuitBreaker(1, 1000);
        try {
            cb.execute(() -> {
                throw new RuntimeException("down");
            });
        } catch (RuntimeException ignored) {
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        assertThrows(CircuitBreakerOpenException.class, () -> cb.execute(() -> "ok"));
    }

    @Test
    void halfOpenTrialSuccessCloses() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(1, 50);
        try {
            cb.execute(() -> {
                throw new RuntimeException("down");
            });
        } catch (RuntimeException ignored) {
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        Thread.sleep(80); // 等待冷却结束
        // 冷却后放行一次试探
        String r = cb.execute(() -> "ok");
        assertEquals("ok", r);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void halfOpenTrialFailureReopens() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(1, 50);
        try {
            cb.execute(() -> {
                throw new RuntimeException("down");
            });
        } catch (RuntimeException ignored) {
        }
        Thread.sleep(80);
        // 试探再次失败 => 重新 OPEN
        try {
            cb.execute(() -> {
                throw new RuntimeException("down again");
            });
        } catch (RuntimeException ignored) {
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void successResetsFailureCount() {
        CircuitBreaker cb = new CircuitBreaker(3, 1000);
        for (int i = 0; i < 2; i++) {
            try {
                cb.execute(() -> {
                    throw new RuntimeException("down");
                });
            } catch (RuntimeException ignored) {
            }
        }
        // 第 3 次成功，不应触发熔断
        assertEquals("ok", cb.execute(() -> "ok"));
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertTrue(cb.allowRequest());
    }
}
