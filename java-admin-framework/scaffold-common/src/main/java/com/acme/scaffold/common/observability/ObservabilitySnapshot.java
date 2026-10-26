package com.acme.scaffold.common.observability;

/**
 * 跨模块可观测指标快照（M27 报表大屏 / 跨模块端口）。
 *
 * <p>承载来自 {@code scaffold-framework} 等底层模块的运行态可观测指标，
 * 例如 N+1 查询疑似留存。采用不可变 record，所有计数为非负长整型；
 * {@code lastBreachAtMillis} 为最近一次越限登记时间（epoch 毫秒），
 * 从未越限时合法取值为 0（注意：0 与「负数经钳制回落的 0」语义一致，均表示无数据）。</p>
 *
 * <p>本快照与底层实现解耦：framework 侧适配器只负责把运行态聚合计数填入本不可变结构，
 * system 看板直接消费，不感知 registry 等具体类型，从而避免 system 反向依赖 framework。</p>
 *
 * @param nPlusOneSuspectTotal    N+1 疑似累计越限次数（自进程启动，单调递增）
 * @param nPlusOneSuspectRetained N+1 疑似当前留存条数（有界缓冲内）
 * @param lastBreachAtMillis      最近一次 N+1 越限登记时间（epoch 毫秒；未越限为 0）
 */
public record ObservabilitySnapshot(
        long nPlusOneSuspectTotal,
        long nPlusOneSuspectRetained,
        long lastBreachAtMillis) {

    /** 空快照哨兵：无可用指标源（如 framework 未装配 / 采集异常）时回落，避免 null 传播。 */
    public static final ObservabilitySnapshot EMPTY = new ObservabilitySnapshot(0, 0, 0);

    /**
     * 非负钳制：任一字段为负数时回落 0（运行态采集异常可能返回 -1 等哨兵值）。
     * 返回新的不可变实例，不修改原对象。
     */
    public ObservabilitySnapshot clampNonNegative() {
        return new ObservabilitySnapshot(
                nPlusOneSuspectTotal < 0 ? 0 : nPlusOneSuspectTotal,
                nPlusOneSuspectRetained < 0 ? 0 : nPlusOneSuspectRetained,
                lastBreachAtMillis < 0 ? 0 : lastBreachAtMillis);
    }
}
