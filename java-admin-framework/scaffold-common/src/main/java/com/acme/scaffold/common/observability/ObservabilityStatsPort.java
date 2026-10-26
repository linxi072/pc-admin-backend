package com.acme.scaffold.common.observability;

/**
 * 跨模块可观测指标数据源端口（M27 报表大屏）。
 *
 * <p>system 看板需要 framework 的运行态可观测指标（如 N+1 查询疑似），
 * 但 system 不可直接依赖 framework（否则形成反向依赖 / 拆库困难）。
 * 因此在 {@code scaffold-common} 定义端口契约，由 framework 提供可选适配器实现，
 * system 以 {@code @Autowired(required=false)} 消费，无实现时回落 {@link ObservabilitySnapshot#EMPTY}。</p>
 *
 * <p>端口契约要求实现方在采集失败时返回 {@link ObservabilitySnapshot#EMPTY} 或抛出——
 * 调用方（{@code DashboardService}）已对所有异常做 try/catch 兜底，确保看板永不因可观测采集失败而中断。</p>
 */
public interface ObservabilityStatsPort {

    /**
     * 返回当前进程的可观测指标快照。
     *
     * @return 不可变快照；无可用数据时返回 {@link ObservabilitySnapshot#EMPTY}，不应返回 null
     */
    ObservabilitySnapshot snapshot();
}
