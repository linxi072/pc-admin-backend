package com.acme.scaffold.common.resilience;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 熔断器状态机单测（V1.1.0 测试覆盖重建立项：scaffold-common 批次）。
 * 覆盖 CLOSED → OPEN → HALF_OPEN → CLOSED/OPEN 全迁移路径。
 */
class CircuitBreakerTest {

    @Test
    void constructorShouldRejectNonPositiveThreshold() {
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new CircuitBreaker(-1, 100));
    }

    @Test
    void shouldOpenAfterConsecutiveFailuresAndFailFast() {
        CircuitBreaker cb = new CircuitBreaker(3, 60_000);
        for (int i = 0; i < 3; i++) {
            assertThrows(IllegalStateException.class, () -> cb.execute(() -> { throw new IllegalStateException("boom"); }));
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        CircuitBreakerOpenException ex = assertThrows(CircuitBreakerOpenException.class,
                () -> cb.execute(() -> "ok"));
        assertTrue(ex.getMessage().contains("熔断"));
    }

    @Test
    void successShouldResetConsecutiveFailures() {
        CircuitBreaker cb = new CircuitBreaker(3, 60_000);
        cb.execute(() -> { throw new RuntimeException("f1"); });
        cb.execute(() -> { throw new RuntimeException("f2"); });
        assertEquals("ok", cb.execute(() -> "ok"));
        cb.execute(() -> { throw new RuntimeException("f3"); });
        // 成功打断连续失败计数，不应熔断
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void shouldHalfOpenAfterCooldownAndCloseOnSuccess() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(1, 50);
        cb.execute(() -> { throw new RuntimeException("boom"); });
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        Thread.sleep(60);
        assertEquals("ok", cb.execute(() -> "ok"));
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void halfOpenProbeFailureShouldReopen() throws InterruptedException {
        CircuitBreaker cb = new CircuitBreaker(1, 50);
        cb.execute(() -> { throw new RuntimeException("boom"); });
        Thread.sleep(60);
        // HALF_OPEN 放行一次试探，失败回到 OPEN
        assertThrows(RuntimeException.class, () -> cb.execute(() -> { throw new RuntimeException("probe"); }));
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void resetShouldForceClosed() {
        CircuitBreaker cb = new CircuitBreaker(1, 0); // openMs=0 不自动恢复
        cb.execute(() -> { throw new RuntimeException("boom"); });
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        cb.reset();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
        assertEquals("ok", cb.execute(() -> "ok"));
    }

    @Test
    void runnableVariantShouldExecuteAndCountFailures() {
        CircuitBreaker cb = new CircuitBreaker(2, 60_000);
        AtomicInteger calls = new AtomicInteger();
        cb.execute((Runnable) calls::incrementAndGet);
        assertEquals(1, calls.get());
        assertThrows(RuntimeException.class, () -> cb.execute((Runnable) () -> { throw new RuntimeException("x"); }));
        assertThrows(RuntimeException.class, () -> cb.execute((Runnable) () -> { throw new RuntimeException("y"); }));
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }
}
