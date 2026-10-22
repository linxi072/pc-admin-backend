package com.acme.scaffold.kafka.gateway.metrics;

/**
 * 端到端延迟预算（对应设计稿 §7 延迟预算图）。
 *
 * <p>各段预算合计 p99 ≈ 75ms（上限 150ms）：ingest 8 + produce 15 + broker 25
 * + consume 17 + deliver 10（以及少量网络抖动）。{@link #within} / {@link #withinBudget}
 * 供 SLO 校验与压测断言复用。
 */
public final class LatencyBudget {

    /** p99 目标预算（ms）。 */
    public static final long BUDGET_MS = 75L;

    /** 硬上限（ms）。 */
    public static final long UPPER_BOUND_MS = 150L;

    public static final long INGEST_MS = 8L;
    public static final long PRODUCE_MS = 15L;
    public static final long BROKER_MS = 25L;
    public static final long CONSUME_MS = 17L;
    public static final long DELIVER_MS = 10L;

    private LatencyBudget() {
    }

    /** 是否在上限以内。 */
    public static boolean within(long observedMs) {
        return observedMs <= UPPER_BOUND_MS;
    }

    /** 是否优于 p99 预算。 */
    public static boolean withinBudget(long observedMs) {
        return observedMs <= BUDGET_MS;
    }
}
