package com.acme.scaffold.common.event.replication;

import java.util.HashMap;
import java.util.Map;

/**
 * 跨区域复制一致性规则（E7 纯函数）：版本单调判定 + 向量时钟偏序 + 冲突消解口径。
 *
 * <p>不依赖 Kafka/Redis/数据库，全部为确定性计算，便于离线单测；供复制总线与接收端去重/裁决共用，
 * 保证「什么该丢弃、什么该接受、并发冲突如何裁决」三处口径同源。</p>
 */
public final class ConsistencyRules {

    private ConsistencyRules() {
    }

    /** 冲突裁决策略：源单元字典序较小者优先（确定性，避免各区域各自为政）。 */
    public enum ConflictPolicy {
        SOURCE_ID_ASC,
        SOURCE_ID_DESC,
        REJECT
    }

    /** 向量时钟偏序结果。 */
    public enum Relation {
        BEFORE,      // other 严格落后于 local
        AFTER,       // other 严格领先 local
        EQUAL,       // 完全相同
        CONCURRENT   // 并发（互不包含）
    }

    /**
     * 按逻辑版本判定是否应处理入站事件：仅当入站版本严格大于已处理版本时处理。
     * 已处理版本为 null（首次）时放行；入站版本为 null 时拒绝。
     */
    public static boolean shouldApply(Long appliedVersion, Long incomingVersion) {
        if (incomingVersion == null) {
            return false;
        }
        return appliedVersion == null || incomingVersion > appliedVersion;
    }

    /**
     * 比较两个向量时钟的偏序关系（缺失的单元计数视为 0）。
     * 用于识别「因果领先/落后」与「并发写冲突」。
     */
    public static Relation compare(Map<String, Long> local, Map<String, Long> remote) {
        boolean localGreater = false;
        boolean remoteGreater = false;
        for (String unit : unionKeys(local, remote)) {
            long l = local == null ? 0L : local.getOrDefault(unit, 0L);
            long r = remote == null ? 0L : remote.getOrDefault(unit, 0L);
            if (l > r) {
                localGreater = true;
            } else if (r > l) {
                remoteGreater = true;
            }
        }
        if (!localGreater && !remoteGreater) {
            return Relation.EQUAL;
        }
        if (localGreater && !remoteGreater) {
            return Relation.BEFORE; // remote 落后于 local
        }
        if (!localGreater) {
            return Relation.AFTER;  // remote 领先 local
        }
        return Relation.CONCURRENT;
    }

    /** 合并向量时钟：每个单元取最大值（并发写合并后的因果上界）。 */
    public static Map<String, Long> merge(Map<String, Long> local, Map<String, Long> remote) {
        Map<String, Long> result = new HashMap<>();
        for (String unit : unionKeys(local, remote)) {
            long l = local == null ? 0L : local.getOrDefault(unit, 0L);
            long r = remote == null ? 0L : remote.getOrDefault(unit, 0L);
            result.put(unit, Math.max(l, r));
        }
        return Map.copyOf(result);
    }

    /**
     * 并发冲突裁决：local/remote 均为 null 时无冲突返回 null；
     * SOURCE_ID_* 按来源单元字典序确定性选择，REJECT 一律拒绝（返回 null 且不合并）。
     */
    public static ReplicationEnvelope resolveConflict(ReplicationEnvelope local,
                                                      ReplicationEnvelope remote,
                                                      ConflictPolicy policy) {
        if (local == null) {
            return remote;
        }
        if (remote == null) {
            return local;
        }
        if (policy == null || policy == ConflictPolicy.REJECT) {
            return null;
        }
        int cmp = String.valueOf(local.sourceUnit()).compareTo(String.valueOf(remote.sourceUnit()));
        boolean takeLocal = policy == ConflictPolicy.SOURCE_ID_ASC ? cmp <= 0 : cmp >= 0;
        return takeLocal ? local : remote;
    }

    /** 递增向量时钟中指定单元的计数（不可变副本）。 */
    public static Map<String, Long> tick(Map<String, Long> clock, String unit) {
        Map<String, Long> result = new HashMap<>();
        if (clock != null) {
            result.putAll(clock);
        }
        result.put(unit, result.getOrDefault(unit, 0L) + 1);
        return Map.copyOf(result);
    }

    private static java.util.Set<String> unionKeys(Map<String, Long> a, Map<String, Long> b) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (a != null) {
            keys.addAll(a.keySet());
        }
        if (b != null) {
            keys.addAll(b.keySet());
        }
        return keys;
    }
}
