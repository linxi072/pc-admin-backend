package com.acme.scaffold.realtime.gateway;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 背压计数器（观测口径）。
 *
 * <p>关键设计：<b>丢弃不是失败</b>。丢最旧（L1）、断链（L2）、熔断拒绝（L3）分别独立计数，
 * 与 bus 的 produced/failed 分开上报，避免「限流丢弃」被误读为「生产失败」而触发错误告警。
 */
public final class BackpressureCounter {

    private final AtomicLong droppedOldest = new AtomicLong();
    private final AtomicLong slowClosed = new AtomicLong();
    private final AtomicLong rateLimited = new AtomicLong();
    private final AtomicLong idleClosed = new AtomicLong();
    private final AtomicLong protocolRejected = new AtomicLong();
    private final AtomicLong authRejected = new AtomicLong();
    private final AtomicLong circuitBreakRejected = new AtomicLong();

    /** 不可变快照（采集方直接读取，无锁）。 */
    public record Snapshot(long droppedOldest, long slowClosed, long rateLimited,
                           long idleClosed, long protocolRejected, long authRejected,
                           long circuitBreakRejected) {

        public static final Snapshot ZERO = new Snapshot(0, 0, 0, 0, 0, 0, 0);
    }

    public void onDropOldest() {
        droppedOldest.incrementAndGet();
    }

    public void onSlowClosed() {
        slowClosed.incrementAndGet();
    }

    public void onRateLimited() {
        rateLimited.incrementAndGet();
    }

    public void onIdleClosed() {
        idleClosed.incrementAndGet();
    }

    public void onProtocolRejected() {
        protocolRejected.incrementAndGet();
    }

    public void onAuthRejected() {
        authRejected.incrementAndGet();
    }

    public void onCircuitBreakRejected() {
        circuitBreakRejected.incrementAndGet();
    }

    public Snapshot snapshot() {
        return new Snapshot(droppedOldest.get(), slowClosed.get(), rateLimited.get(),
                idleClosed.get(), protocolRejected.get(), authRejected.get(),
                circuitBreakRejected.get());
    }
}
