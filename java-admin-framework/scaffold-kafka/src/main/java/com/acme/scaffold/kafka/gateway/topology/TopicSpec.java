package com.acme.scaffold.kafka.gateway.topology;

import com.acme.scaffold.common.messaging.domain.Tier;

/**
 * 单个 topic 的分级规格（对应设计稿 §2.3 表格一行的结构化表达）。
 *
 * @param name                topic 名
 * @param tier                通道分级
 * @param partitions          分区数（并行度上限，只能增不能减，§4.2）
 * @param replicationFactor   副本数（§8.1：跨 AZ 均布，默认 3）
 * @param minInsyncReplicas   最小同步副本（§8.1：默认 2，保 acks=all 不丢数据）
 * @param retentionMs         保留时长
 * @param acks                投递语义（"1" / "all"）
 * @param lingerMs            批量窗口（延迟/吞吐的最大调节项，§7.2 第 1 条）
 * @param compression         压缩算法（实现取 zstd，见 README 与设计稿的差异说明）
 */
public record TopicSpec(String name, Tier tier, int partitions, int replicationFactor,
                        int minInsyncReplicas, long retentionMs, String acks,
                        int lingerMs, String compression) {

    public TopicSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("topic 名不能为空");
        }
        if (partitions <= 0) {
            throw new IllegalArgumentException("分区数必须为正: " + partitions);
        }
        if (replicationFactor <= 0) {
            throw new IllegalArgumentException("副本数必须为正: " + replicationFactor);
        }
        if (minInsyncReplicas <= 0 || minInsyncReplicas > replicationFactor) {
            throw new IllegalArgumentException("min.insync.replicas 必须在 1.." + replicationFactor
                    + " 之间: " + minInsyncReplicas);
        }
        if (retentionMs <= 0) {
            throw new IllegalArgumentException("保留时长必须为正: " + retentionMs);
        }
        if (!TopologyRules.isValidAcks(acks)) {
            throw new IllegalArgumentException("acks 仅支持 0/1/all: " + acks);
        }
        if (lingerMs < 0) {
            throw new IllegalArgumentException("linger.ms 不能为负: " + lingerMs);
        }
        if (compression == null || compression.isBlank()) {
            throw new IllegalArgumentException("压缩算法不能为空");
        }
    }
}
