package com.acme.scaffold.kafka.gateway.topology;

import com.acme.scaffold.common.messaging.domain.Tier;

/**
 * Topic 命名规范（对应设计稿 §2.3 分级表 + §3.6 补洞 topic）。
 *
 * <p>命名即契约：名字中承载分级（rt/durable/fanout/dlq）与分片维度，
 * 运维排障时仅凭 topic 名即可判定 SLA 档位。
 */
public final class TopicNames {

    /** 实时计算通道（§2.3：acks=1、保留 6h）。 */
    public static final String RAW_RT = "msg.raw.rt";

    /** 持久化/审计通道（§2.3：acks=all、保留 72h）。 */
    public static final String RAW_DURABLE = "msg.raw.durable";

    /** 死信通道（§2.3：保留 7d）。 */
    public static final String DLQ = "msg.dlq";

    /** 补洞 topic 模板：{@code msg.userseq.<uid % 128>}（§3.6：compacted，键=seq）。 */
    public static final String USER_SEQ_PREFIX = "msg.userseq.";

    /** 补洞 topic 槽位数（§3.6）。 */
    public static final int USER_SEQ_SLOTS = 128;

    private TopicNames() {
    }

    /** 跨网关扇出 topic：{@code msg.fanout.<shard>}（§4.4，分区键=分片 ID）。 */
    public static String fanout(int shard) {
        if (shard < 0) {
            throw new IllegalArgumentException("分片号不能为负: " + shard);
        }
        return "msg.fanout." + shard;
    }

    /** 补洞 topic 名（按用户槽位）。 */
    public static String userSeq(int slot) {
        if (slot < 0 || slot >= USER_SEQ_SLOTS) {
            throw new IllegalArgumentException("补洞槽位越界(0.." + (USER_SEQ_SLOTS - 1) + "): " + slot);
        }
        return USER_SEQ_PREFIX + slot;
    }

    /** 按通道取主 topic 名（FANOUT 需指定分片，故不在此列）。 */
    public static String primary(Tier tier) {
        return switch (tier) {
            case RT -> RAW_RT;
            case DURABLE -> RAW_DURABLE;
            case DLQ -> DLQ;
            case FANOUT -> throw new IllegalArgumentException("FANOUT 通道按分片命名，请使用 fanout(shard)");
        };
    }
}
