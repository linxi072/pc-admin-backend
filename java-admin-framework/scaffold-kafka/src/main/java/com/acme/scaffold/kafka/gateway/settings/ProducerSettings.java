package com.acme.scaffold.kafka.gateway.settings;

/**
 * 分级 Producer 参数（对应设计稿 §2.3 双 topic 分级表）。
 *
 * <p>RT 通道：acks=1、linger 小、批量小、保留 6h（低延迟、低持久）。
 * DURABLE 通道：acks=all、linger 大、批量大、保留 72h（高持久、可重放）。
 */
public record ProducerSettings(int acks, long lingerMs, int batchSize, String compression, long retentionMs) {

    /** 实时通道参数（§2.3：acks=1，保留 6h）。 */
    public static ProducerSettings rt() {
        return new ProducerSettings(1, 5, 16 * 1024, "zstd", 6L * 3600_000);
    }

    /** 持久化通道参数（§2.3：acks=all，保留 72h）。 */
    public static ProducerSettings durable() {
        return new ProducerSettings(-1, 20, 64 * 1024, "zstd", 72L * 3600_000);
    }
}
