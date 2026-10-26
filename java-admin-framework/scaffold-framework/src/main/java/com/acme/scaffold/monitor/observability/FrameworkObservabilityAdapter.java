package com.acme.scaffold.monitor.observability;

import com.acme.scaffold.common.observability.ObservabilitySnapshot;
import com.acme.scaffold.common.observability.ObservabilityStatsPort;
import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import org.springframework.stereotype.Component;

/**
 * 跨模块可观测指标适配器（M27 / framework 侧）。
 *
 * <p>实现 {@code scaffold-common} 的 {@link ObservabilityStatsPort}，将 framework 运行态
 * N+1 疑似留存暴露给 system 看板，使「可观测指标」与「系统域计数」在同一总览快照中聚合展示。</p>
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>只读无副作用</b>：仅读取 {@link NPlusOneSuspectRegistry} 的聚合计数（total/size/lastBreachAt）；</li>
 *   <li><b>非负钳制</b>：经 {@link ObservabilitySnapshot#clampNonNegative()} 兜底，杜绝负数传播到看板；</li>
 *   <li><b>随 framework 装配</b>：本 Bean 仅存在于 framework classpath，system 以可选注入消费，
 *       未装配时 system 自动回落 {@link ObservabilitySnapshot#EMPTY}，零耦合、零回归。</li>
 * </ul>
 * </p>
 */
@Component
public class FrameworkObservabilityAdapter implements ObservabilityStatsPort {

    private final NPlusOneSuspectRegistry registry;

    public FrameworkObservabilityAdapter(NPlusOneSuspectRegistry registry) {
        this.registry = registry;
    }

    @Override
    public ObservabilitySnapshot snapshot() {
        return new ObservabilitySnapshot(
                registry.total(),
                registry.size(),
                registry.lastBreachAt()).clampNonNegative();
    }
}
