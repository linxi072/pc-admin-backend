package com.acme.scaffold.realtime.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.acme.scaffold.realtime.gateway.BackpressureCounter.Snapshot;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** 背压计数器单测：独立计数语义与并发原子性。 */
class BackpressureCounterTest {

    @Test
    void freshCounterShouldBeZero() {
        assertEquals(Snapshot.ZERO, new BackpressureCounter().snapshot());
    }

    @Test
    void eachEventShouldIncrementOnlyItsOwnBucket() {
        BackpressureCounter c = new BackpressureCounter();
        c.onDropOldest();
        c.onDropOldest();
        c.onSlowClosed();
        c.onRateLimited();
        c.onIdleClosed();
        c.onProtocolRejected();
        c.onAuthRejected();
        c.onCircuitBreakRejected();

        assertEquals(new Snapshot(2, 1, 1, 1, 1, 1, 1), c.snapshot());
    }

    @Test
    void snapshotShouldBeImmutablePointInTime() {
        BackpressureCounter c = new BackpressureCounter();
        Snapshot first = c.snapshot();
        c.onDropOldest();
        assertEquals(0L, first.droppedOldest());
        assertEquals(1L, c.snapshot().droppedOldest());
    }

    @Test
    void concurrentIncrementsShouldNotLoseUpdates() throws Exception {
        BackpressureCounter c = new BackpressureCounter();
        int threads = 8;
        int perThread = 1000;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger();
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int j = 0; j < perThread; j++) {
                        c.onCircuitBreakRejected();
                    }
                } catch (InterruptedException e) {
                    errors.incrementAndGet();
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        start.countDown();
        done.await();
        assertEquals(0, errors.get());
        assertEquals((long) threads * perThread, c.snapshot().circuitBreakRejected());
    }
}
