package com.acme.scaffold.common.event.replication;

import com.acme.scaffold.common.event.DomainEvent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * 跨区域复制总线（E7）：领域事件 → 信封 → 按拓扑扇出到目标单元。
 *
 * <p><b>零回归设计</b>：本类<b>不</b>实现 {@code EventBridge}、不参与默认事件发布的自动桥接发现
 * （避免与 {@code NoOpEventBridge} 冲突）。仅在调用方显式注册 {@link ReplicationTarget} 后才产生复制行为；
 * 未配置拓扑/目标时 {@link #replicate} 返回空目标列表且仅做幂等登记，不影响本地同步事件语义。</p>
 *
 * <p>去重口径：同 eventId 仅投递一次（幂等），重复投递计入统计但不重复扇出；
 * 目标列表由 {@link ReplicationTopology} 计算（排除源单元与离线单元）。</p>
 */
@Component
public class ReplicationBus {

    /** 复制目标端口：由外部 jar（如 Kafka 适配器）实现具体投递，框架本体零依赖。 */
    @FunctionalInterface
    public interface ReplicationTarget {
        void deliver(ReplicationEnvelope envelope, String targetUnit);
    }

    /** 去重表：eventId → 版本号；只保留近期事件（有界，避免无界增长）。 */
    private static final int MAX_SEEN = 10_000;
    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ReplicationTarget> targets = new CopyOnWriteArrayList<>();
    private final AtomicLong replicatedCount = new AtomicLong();
    private final AtomicLong duplicateCount = new AtomicLong();

    private volatile ReplicationTopology topology = new ReplicationTopology(Map.of());

    /** 配置复制拓扑（未调用时为空拓扑，任何事件都无目标）。 */
    public void configure(ReplicationTopology topology) {
        this.topology = topology == null ? new ReplicationTopology(Map.of()) : topology;
    }

    /** 注册复制目标（可注册多个，如 Kafka / 日志 / 测试桩）。 */
    public void register(ReplicationTarget target) {
        if (target != null) {
            targets.add(target);
        }
    }

    /**
     * 复制一个领域事件，返回实际扇出的目标单元列表。
     * 重复 eventId 直接返回空列表（幂等）；目标单元为空时不投递但仍登记去重。
     */
    public List<String> replicate(DomainEvent event, String sourceUnit, long version) {
        if (event == null) {
            return List.of();
        }
        Long previous = seen.putIfAbsent(event.getEventId(), version);
        if (previous != null) {
            duplicateCount.incrementAndGet();
            return List.of();
        }
        evictIfNeeded();
        List<String> targetUnits = topology.targetsFrom(sourceUnit);
        if (targetUnits.isEmpty()) {
            return List.of();
        }
        ReplicationEnvelope envelope = ReplicationEnvelope.of(event, sourceUnit, targetUnits, version);
        for (String unit : targetUnits) {
            for (ReplicationTarget target : targets) {
                target.deliver(envelope, unit);
            }
            replicatedCount.incrementAndGet();
        }
        return targetUnits;
    }

    /** 复制统计快照（供运维/测试断言）。 */
    public Stats stats() {
        return new Stats(replicatedCount.get(), duplicateCount.get(), seen.size(), targets.size());
    }

    /** 清空去重表与统计（仅供测试/重建拓扑时调用）。 */
    public void reset() {
        seen.clear();
        replicatedCount.set(0);
        duplicateCount.set(0);
    }

    private void evictIfNeeded() {
        if (seen.size() > MAX_SEEN) {
            // 简单有界：清空最老的一半（ConcurrentHashMap 无顺序，按插入近似淘汰）
            int toRemove = seen.size() / 2;
            int removed = 0;
            var it = seen.keySet().iterator();
            while (it.hasNext() && removed++ < toRemove) {
                it.next();
                it.remove();
            }
        }
    }

    public record Stats(long replicated, long duplicates, int trackedEvents, int registeredTargets) {
    }
}
