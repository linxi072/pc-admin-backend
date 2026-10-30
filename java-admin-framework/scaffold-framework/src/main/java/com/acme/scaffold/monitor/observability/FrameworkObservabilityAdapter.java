package com.acme.scaffold.monitor.observability;

import com.acme.scaffold.common.observability.ObservabilitySnapshot;
import com.acme.scaffold.common.observability.ObservabilityStatsPort;
import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import com.acme.scaffold.observability.recorder.SpanRecorder;
import org.springframework.stereotype.Component;

/**
 * 跨模块可观测指标适配器（M27 / framework 侧）。
 *
 * <p>实现 {@code scaffold-common} 的 {@link ObservabilityStatsPort}，将 framework 运行态
 * 可观测指标暴露给 system 看板，目前聚合两个维度：
 * <ul>
 *   <li>N+1 查询疑似（来自 {@link NPlusOneSuspectRegistry}）；</li>
 *   <li>链路追踪本地 span 留存（来自 {@link SpanRecorder}）。</li>
 * </ul>
 * 使「可观测指标」与「系统域计数」在同一总览快照中聚合展示。</p>
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>只读无副作用</b>：仅读取 registry 的聚合计数（total/size/lastBreachAt）与
 *       recorder 的运行态统计（enabled/size/OK+ERROR+UNSET 累计），不触网、不落库；</li>
 *   <li><b>非负钳制</b>：经 {@link ObservabilitySnapshot#clampNonNegative()} 兜底，杜绝负数传播到看板；</li>
 *   <li><b>随 framework 装配</b>：本 Bean 仅存在于 framework classpath，system 以可选注入消费，
 *       未装配时 system 自动回落 {@link ObservabilitySnapshot#EMPTY}，零耦合、零回归。</li>
 * </ul>
 * </p>
 */
@Component
public class FrameworkObservabilityAdapter implements ObservabilityStatsPort {

    private final NPlusOneSuspectRegistry registry;
    private final SpanRecorder recorder;

    public FrameworkObservabilityAdapter(NPlusOneSuspectRegistry registry, SpanRecorder recorder) {
        this.registry = registry;
        this.recorder = recorder;
    }

    @Override
    public ObservabilitySnapshot snapshot() {
        SpanRecorder.RecorderStats stats = recorder.stats();
        long traceTotal = stats.okCount() + stats.errorCount() + stats.unsetCount();
        return new ObservabilitySnapshot(
                registry.total(),
                registry.size(),
                registry.lastBreachAt(),
                stats.enabled(),
                stats.size(),
                traceTotal,
                stats.errorCount()).clampNonNegative();
    }
}
