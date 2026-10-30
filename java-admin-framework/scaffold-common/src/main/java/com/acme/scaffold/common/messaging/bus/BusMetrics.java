package com.acme.scaffold.common.messaging.bus;

import com.acme.scaffold.common.messaging.domain.Tier;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 总线指标快照（对应设计稿 §9 观测体系：produced / rejected / failed 分通道计数）。
 *
 * <p>不可变快照语义：采集方持对象即可安全读取，无需加锁。
 */
public record BusMetrics(long produced, long rejected, long failed, Map<Tier, Long> byTier) {

    private static final BusMetrics ZERO = new BusMetrics(0, 0, 0, Map.of());

    public static BusMetrics zero() {
        return ZERO;
    }

    /** 合并构造（内部使用，外部拿快照即可）。 */
    public static BusMetrics of(long produced, long rejected, long failed, Map<Tier, Long> byTier) {
        EnumMap<Tier, Long> m = new EnumMap<>(Tier.class);
        if (byTier != null) {
            m.putAll(byTier);
        }
        return new BusMetrics(produced, rejected, failed, Collections.unmodifiableMap(m));
    }
}
