package com.acme.scaffold.kafka.gateway.topology;

import com.acme.scaffold.common.messaging.domain.Tier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集群 Topic 拓扑（对应设计稿 §2.3 分级表 + §4.2 分区数公式 + §4.4 扇出分片）。
 *
 * <p>集中持有全部 topic 规格，是「拓扑即配置」的单一事实来源：
 * TopicAdmin 据此幂等建 topic，KafkaBus 据此选 producer 参数，消费组据此订阅。
 */
public final class Topology {

    private final Map<String, TopicSpec> specs;

    private Topology(Map<String, TopicSpec> specs) {
        this.specs = Collections.unmodifiableMap(specs);
    }

    /**
     * 设计稿默认拓扑（参数全部来自 §2.3 / §4.2 / §4.4 / §3.6）。
     *
     * @param partitions     RT/DURABLE 主通道分区数（默认 144，§4.2 推导）
     * @param fanoutShards   扇出分片数（默认 64，§4.4）
     */
    public static Topology designDefault(int partitions, int fanoutShards) {
        Map<String, TopicSpec> map = new LinkedHashMap<>();
        map.put(TopicNames.RAW_RT, new TopicSpec(
                TopicNames.RAW_RT, Tier.RT, partitions, 3, 2,
                6L * 3600_000, "1", 5, "zstd"));
        map.put(TopicNames.RAW_DURABLE, new TopicSpec(
                TopicNames.RAW_DURABLE, Tier.DURABLE, partitions, 3, 2,
                72L * 3600_000, "all", 20, "zstd"));
        for (int shard = 0; shard < fanoutShards; shard++) {
            String name = TopicNames.fanout(shard);
            map.put(name, new TopicSpec(
                    name, Tier.FANOUT, partitions, 3, 2,
                    2L * 3600_000, "1", 5, "zstd"));
        }
        map.put(TopicNames.DLQ, new TopicSpec(
                TopicNames.DLQ, Tier.DLQ, 12, 3, 2,
                7L * 24 * 3600_000, "all", 50, "none"));
        return new Topology(map);
    }

    /** 按主通道分区的设计默认值（144 分区 / 64 扇出分片）。 */
    public static Topology designDefault() {
        return designDefault(144, 64);
    }

    /**
     * 自定义拓扑（测试/特殊部署用）。
     *
     * @throws IllegalArgumentException 存在同名 topic
     */
    public static Topology of(java.util.Collection<TopicSpec> specs) {
        Map<String, TopicSpec> map = new LinkedHashMap<>();
        for (TopicSpec spec : specs) {
            if (map.putIfAbsent(spec.name(), spec) != null) {
                throw new IllegalArgumentException("同名 topic 重复定义: " + spec.name());
            }
        }
        if (map.isEmpty()) {
            throw new IllegalArgumentException("拓扑不能为空");
        }
        return new Topology(map);
    }

    public TopicSpec spec(String topicName) {
        return specs.get(topicName);
    }

    /** 按通道取主 topic 规格（FANOUT 按分片查）。 */
    public TopicSpec specOf(Tier tier) {
        if (tier == Tier.FANOUT) {
            throw new IllegalArgumentException("FANOUT 按分片查询，请使用 spec(fanout(shard))");
        }
        TopicSpec spec = specs.get(TopicNames.primary(tier));
        if (spec == null) {
            throw new IllegalStateException("拓扑缺少主通道规格: " + tier);
        }
        return spec;
    }

    public TopicSpec fanoutSpec(int shard) {
        TopicSpec spec = specs.get(TopicNames.fanout(shard));
        if (spec == null) {
            throw new IllegalArgumentException("扇出分片越界: " + shard);
        }
        return spec;
    }

    /** 全部规格（保持插入序：rt/durable → fanout* → dlq）。 */
    public List<TopicSpec> allSpecs() {
        return List.copyOf(specs.values());
    }

    /** 补洞 topic 名清单（§3.6：128 个 compacted topic 由 TopicAdmin 另行创建）。 */
    public List<String> userSeqTopicNames() {
        List<String> names = new ArrayList<>(TopicNames.USER_SEQ_SLOTS);
        for (int slot = 0; slot < TopicNames.USER_SEQ_SLOTS; slot++) {
            names.add(TopicNames.userSeq(slot));
        }
        return names;
    }

    /** 全量校验：返回人类可读的问题清单（空 = 通过）。 */
    public List<String> validate() {
        List<String> problems = new ArrayList<>();
        for (TopicSpec spec : specs.values()) {
            if (spec.tier() == Tier.RT && !"1".equals(spec.acks())) {
                problems.add(spec.name() + ": RT 通道应为 acks=1（§2.3）");
            }
            if (spec.tier() == Tier.DURABLE && !"all".equals(spec.acks())) {
                problems.add(spec.name() + ": DURABLE 通道应为 acks=all（§2.3）");
            }
            if (spec.replicationFactor() < 3) {
                problems.add(spec.name() + ": 副本数建议 ≥3（§8.1 跨 AZ 冗余）");
            }
        }
        return problems;
    }
}
