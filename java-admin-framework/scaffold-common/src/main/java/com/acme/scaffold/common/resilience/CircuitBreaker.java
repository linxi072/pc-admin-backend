package com.acme.scaffold.common.resilience;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 轻量级熔断器（零外部依赖，离线可构建）。
 *
 * <p>状态机：{@code CLOSED → OPEN → HALF_OPEN → CLOSED/OPEN}。
 * <ul>
 *   <li>{@code CLOSED}：正常放行；连续失败达到阈值后切换到 {@code OPEN}。</li>
 *   <li>{@code OPEN}：在 {@code openMs} 冷却期内直接拒绝（fail-fast），避免对持续故障的下游反复重试/堆积；
 *       冷却结束后进入 {@code HALF_OPEN} 放行一次试探。</li>
 *   <li>{@code HALF_OPEN}：仅放行一次试探请求；成功则回到 {@code CLOSED}，失败则重新 {@code OPEN}。</li>
 * </ul>
 *
 * <p>线程安全：状态迁移在内部锁内完成，可安全用于并发调用。</p>
 *
 * <p><b>使用</b>：用 {@link #execute} 包裹下游调用；下游抛 {@link RuntimeException} 计为失败，
 * 正常返回计为成功。需在 {@code OPEN} 期快速失败时捕获 {@link CircuitBreakerOpenException}。</p>
 */
public final class CircuitBreaker {

    /** 熔断器状态。 */
    public enum State {
        /** 关闭：正常放行。 */
        CLOSED,
        /** 打开：冷却期内拒绝所有请求。 */
        OPEN,
        /** 半开：冷却结束，放行一次试探。 */
        HALF_OPEN
    }

    private final int failureThreshold;
    private final long openMs;

    private final Object lock = new Object();
    private State state = State.CLOSED;
    private int consecutiveFailures = 0;
    private long openedAt = 0L;

    private final AtomicInteger failureCountView = new AtomicInteger(0);
    private final AtomicLong openedAtView = new AtomicLong(0L);

    /**
     * @param failureThreshold 连续失败达到该值即熔断（≥1）
     * @param openMs           OPEN 状态冷却时长（毫秒，≥0）；≤0 表示不自动恢复（需手动 {@link #reset}）
     */
    public CircuitBreaker(int failureThreshold, long openMs) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold 必须 ≥ 1");
        }
        this.failureThreshold = failureThreshold;
        this.openMs = Math.max(0, openMs);
    }

    /** 当前状态（快照，仅用于观测/测试）。 */
    public State getState() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * 是否允许本次请求放行。{@code OPEN} 且冷却未结束返回 false；
     * {@code OPEN} 冷却结束则切 {@code HALF_OPEN} 并放行一次试探。
     */
    public boolean allowRequest() {
        synchronized (lock) {
            if (state == State.OPEN) {
                if (openMs > 0 && System.currentTimeMillis() - openedAt >= openMs) {
                    state = State.HALF_OPEN;
                    return true;
                }
                return false;
            }
            return true;
        }
    }

    /**
     * 执行受保护任务；熔断拒绝时抛 {@link CircuitBreakerOpenException}，
     * 任务抛 {@link RuntimeException} 计为失败并原样上抛。
     */
    public <T> T execute(java.util.function.Supplier<T> task) {
        if (!allowRequest()) {
            throw new CircuitBreakerOpenException("熔断已开启，快速失败（state=" + getState() + "）");
        }
        try {
            T result = task.get();
            onSuccess();
            return result;
        } catch (RuntimeException ex) {
            onFailure();
            throw ex;
        }
    }

    /** 无返回值变体，等价于 {@code execute(() -> { task.run(); return null; })}。 */
    public void execute(java.lang.Runnable task) {
        execute(() -> {
            task.run();
            return null;
        });
    }

    private void onSuccess() {
        synchronized (lock) {
            consecutiveFailures = 0;
            failureCountView.set(0);
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
            }
        }
    }

    private void onFailure() {
        synchronized (lock) {
            consecutiveFailures++;
            failureCountView.set(consecutiveFailures);
            if (state == State.HALF_OPEN) {
                state = State.OPEN;
                openedAt = System.currentTimeMillis();
                openedAtView.set(openedAt);
            } else if (consecutiveFailures >= failureThreshold) {
                state = State.OPEN;
                openedAt = System.currentTimeMillis();
                openedAtView.set(openedAt);
            }
        }
    }

    /** 手动复位到 {@code CLOSED}（运维干预 / 测试）。 */
    public void reset() {
        synchronized (lock) {
            state = State.CLOSED;
            consecutiveFailures = 0;
            openedAt = 0L;
            failureCountView.set(0);
            openedAtView.set(0L);
        }
    }
}
