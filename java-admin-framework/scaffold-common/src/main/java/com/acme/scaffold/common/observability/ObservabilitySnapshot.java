package com.acme.scaffold.common.observability;

/**
 * 跨模块可观测指标快照（M27 报表大屏 / 跨模块端口）。
 *
 * <p>承载来自 {@code scaffold-framework} 等底层模块的运行态可观测指标，目前覆盖两个维度：
 * <ul>
 *   <li><b>N+1 查询疑似</b>：累计越限次数 / 当前留存条数 / 最近越限时间（来自 {@code NPlusOneSuspectRegistry}）；</li>
 *   <li><b>链路追踪（本地 span 记录器）</b>：记录器是否启用 / 当前留存 span 数 / 历史累计 span 数 / 历史 ERROR span 数
 *       （来自 framework 的 {@code SpanRecorder}）。</li>
 * </ul>
 * 采用不可变 record，所有计数为非负长整型；时间类字段为 epoch 毫秒（未发生时合法取值为 0）。</p>
 *
 * <p>本快照与底层实现解耦：framework 侧适配器只负责把运行态聚合计数填入本不可变结构，
 * system 看板直接消费，不感知 registry / recorder 等具体类型，从而避免 system 反向依赖 framework。</p>
 *
 * @param nPlusOneSuspectTotal    N+1 疑似累计越限次数（自进程启动，单调递增）
 * @param nPlusOneSuspectRetained N+1 疑似当前留存条数（有界缓冲内）
 * @param lastBreachAtMillis      最近一次 N+1 越限登记时间（epoch 毫秒；未越限为 0）
 * @param traceRecorderEnabled    本地 span 记录器是否启用（由 {@code observability.recorder.enabled} 控制）
 * @param traceRetained           当前留存的 span 条数（环形缓冲内，即 {@code SpanRecorder.size()}）
 * @param traceTotal              历史累计记录的 span 条数（含已被淘汰的，即 OK+ERROR+UNSET 累计）
 * @param traceErrorTotal         历史累计 ERROR 状态的 span 条数（含已被淘汰的）
 */
public record ObservabilitySnapshot(
        long nPlusOneSuspectTotal,
        long nPlusOneSuspectRetained,
        long lastBreachAtMillis,
        boolean traceRecorderEnabled,
        long traceRetained,
        long traceTotal,
        long traceErrorTotal) {

    /** 空快照哨兵：无可用指标源（如 framework 未装配 / 采集异常）时回落，避免 null 传播。 */
    public static final ObservabilitySnapshot EMPTY =
            new ObservabilitySnapshot(0, 0, 0, false, 0, 0, 0);

    /**
     * 非负钳制：任一 long 型计数字段为负数时回落 0（运行态采集异常可能返回 -1 等哨兵值）；
     * boolean 型 {@code traceRecorderEnabled} 原样保留（启用态不受数值异常影响）。
     * 返回新的不可变实例，不修改原对象。
     */
    public ObservabilitySnapshot clampNonNegative() {
        return new ObservabilitySnapshot(
                nPlusOneSuspectTotal < 0 ? 0 : nPlusOneSuspectTotal,
                nPlusOneSuspectRetained < 0 ? 0 : nPlusOneSuspectRetained,
                lastBreachAtMillis < 0 ? 0 : lastBreachAtMillis,
                traceRecorderEnabled,
                traceRetained < 0 ? 0 : traceRetained,
                traceTotal < 0 ? 0 : traceTotal,
                traceErrorTotal < 0 ? 0 : traceErrorTotal);
    }
}
