package com.acme.scaffold.kafka.gateway.router;

import com.acme.scaffold.kafka.rules.OrderingRules;

import java.util.ArrayList;
import java.util.List;

/**
 * 扇出路由（对应设计稿 §4.4：本地展开 64 分片，不落 Kafka）。
 *
 * <p>上行一个实体消息在网关节点内展开为最多 64 个本地扇出目标（按 uid 哈希分片），
 * 由节点本地消费者并行处理；这是「扇出不落 Kafka」的实现要点——扇出在网关内存完成，
 * 不往 Kafka 写 N 份拷贝。
 *
 * <p>分片哈希复用框架统一的 {@link OrderingRules#partitionForKey}（murmur2，位级兼容 Kafka 默认分区器）。
 */
public final class FanoutRouter {

    /** 扇出分片数（§4.4）。 */
    public static final int SHARD_COUNT = 64;

    private FanoutRouter() {
    }

    /** 给定业务键的归属扇出分片（hash(key) % 64）。 */
    public static int shardOf(String key) {
        return OrderingRules.partitionForKey(key, SHARD_COUNT);
    }

    /**
     * 展开为本地扇出目标分片。
     *
     * @param key       业务键（通常为 uid）
     * @param broadcast true=广播到全部 64 分片（大房间/全量推送）；false=仅归属分片
     */
    public static List<Integer> expand(String key, boolean broadcast) {
        if (broadcast) {
            List<Integer> all = new ArrayList<>(SHARD_COUNT);
            for (int i = 0; i < SHARD_COUNT; i++) {
                all.add(i);
            }
            return all;
        }
        return List.of(shardOf(key));
    }
}
