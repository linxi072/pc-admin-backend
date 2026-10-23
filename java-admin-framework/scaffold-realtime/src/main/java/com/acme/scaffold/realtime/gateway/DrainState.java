package com.acme.scaffold.realtime.gateway;

/**
 * 优雅退出七步状态机（对应设计稿 §3.6「优雅退出七步」）。
 *
 * <p>步骤顺序与超时：
 * <ol>
 *   <li>{@link DrainStep#HEALTH_DRAINING}  健康检查返回 draining，LB 摘除本节点；</li>
 *   <li>{@link DrainStep#ACCEPT_STOPPED}   停止接受新握手；</li>
 *   <li>{@link DrainStep#ROUTES_MIGRATING} 通知路由表：本节点连接标记 migrating；</li>
 *   <li>{@link DrainStep#INFLIGHT_DRAINING} 排空 in-flight（水位归零或 30s 超时）；</li>
 *   <li>{@link DrainStep#CLIENTS_CLOSING}  向客户端发 1001（带 reconnect 头，立即重连）；</li>
 *   <li>{@link DrainStep#PRODUCER_FLUSHING} flush producer（同步等待，5s 强制退出）；</li>
 *   <li>{@link DrainStep#TERMINATED}       删除路由表条目，进程退出。</li>
 * </ol>
 *
 * <p>推进判定为纯函数（{@link #advance}）：可完整单测每一步的迁移与超时兜底。
 */
public final class DrainState {

    /** 排空 in-flight 的超时（§3.6 第 4 步：30s）。 */
    public static final long DEFAULT_INFLIGHT_TIMEOUT_MS = 30_000L;

    /** producer flush 超时（§3.6 第 6 步：5s 强制退出）。 */
    public static final long DEFAULT_FLUSH_TIMEOUT_MS = 5_000L;

    /**
     * 推进信号（外部观测输入）。
     *
     * @param inflightCount    节点 in-flight 总量（发送队列 + 生产队列）
     * @param openConnections  存活连接数
     * @param producerFlushed  producer flush 是否完成
     * @param elapsedInStepMs  当前步骤已停留时长
     */
    public record Signal(int inflightCount, int openConnections, boolean producerFlushed, long elapsedInStepMs) {

        public static Signal of(int inflightCount, int openConnections, boolean producerFlushed, long elapsedInStepMs) {
            return new Signal(inflightCount, openConnections, producerFlushed, elapsedInStepMs);
        }
    }

    private final long inflightTimeoutMs;
    private final long flushTimeoutMs;
    private DrainStep step;
    private long stepStartedAt;

    public DrainState(long inflightTimeoutMs, long flushTimeoutMs) {
        if (inflightTimeoutMs <= 0 || flushTimeoutMs <= 0) {
            throw new IllegalArgumentException("超时必须为正");
        }
        this.inflightTimeoutMs = inflightTimeoutMs;
        this.flushTimeoutMs = flushTimeoutMs;
        this.step = DrainStep.RUNNING;
        this.stepStartedAt = 0L;
    }

    public static DrainState designDefault() {
        return new DrainState(DEFAULT_INFLIGHT_TIMEOUT_MS, DEFAULT_FLUSH_TIMEOUT_MS);
    }

    public DrainStep step() {
        return step;
    }

    public long stepStartedAt() {
        return stepStartedAt;
    }

    public boolean isDraining() {
        return step != DrainStep.RUNNING && step != DrainStep.TERMINATED;
    }

    public boolean isTerminated() {
        return step == DrainStep.TERMINATED;
    }

    /**
     * 按当前信号推进一步；返回推进后的步骤（可能不变）。
     *
     * @param now 当前时间（用于记录步骤进入时刻）
     */
    public DrainStep advance(Signal signal, long now) {
        if (step == DrainStep.TERMINATED) {
            return step;
        }
        if (step == DrainStep.RUNNING) {
            return enter(DrainStep.HEALTH_DRAINING, now);
        }
        if (step == DrainStep.INFLIGHT_DRAINING) {
            boolean drained = signal.inflightCount() <= 0 || signal.elapsedInStepMs() >= inflightTimeoutMs;
            return drained ? enter(DrainStep.CLIENTS_CLOSING, now) : step;
        }
        if (step == DrainStep.CLIENTS_CLOSING) {
            return signal.openConnections() <= 0 ? enter(DrainStep.PRODUCER_FLUSHING, now) : step;
        }
        if (step == DrainStep.PRODUCER_FLUSHING) {
            boolean done = signal.producerFlushed() || signal.elapsedInStepMs() >= flushTimeoutMs;
            return done ? enter(DrainStep.TERMINATED, now) : step;
        }
        // 其余步骤为网关侧原子动作，收到推进信号即进入下一步
        DrainStep next = switch (step) {
            case HEALTH_DRAINING -> DrainStep.ACCEPT_STOPPED;
            case ACCEPT_STOPPED -> DrainStep.ROUTES_MIGRATING;
            case ROUTES_MIGRATING -> DrainStep.INFLIGHT_DRAINING;
            default -> throw new IllegalStateException("未覆盖的排空步骤: " + step);
        };
        return enter(next, now);
    }

    private DrainStep enter(DrainStep next, long now) {
        this.step = next;
        this.stepStartedAt = now;
        return next;
    }

    /** 七步骤枚举（§3.6）。 */
    public enum DrainStep {
        /** 正常运行（未开始排空）。 */
        RUNNING,
        /** 1. 健康检查 draining，LB 摘除。 */
        HEALTH_DRAINING,
        /** 2. 停止新握手。 */
        ACCEPT_STOPPED,
        /** 3. 路由表标记 migrating。 */
        ROUTES_MIGRATING,
        /** 4. 排空 in-flight（30s 超时兜底）。 */
        INFLIGHT_DRAINING,
        /** 5. 客户端 1001 关闭（reconnect 头）。 */
        CLIENTS_CLOSING,
        /** 6. producer flush（5s 强制）。 */
        PRODUCER_FLUSHING,
        /** 7. 路由表清理，进程退出。 */
        TERMINATED
    }
}
