package com.acme.scaffold.kafka.gateway;

import com.acme.scaffold.common.messaging.bus.BusMetrics;
import com.acme.scaffold.common.messaging.bus.MessageBus;
import com.acme.scaffold.common.messaging.bus.ProduceResult;
import com.acme.scaffold.common.messaging.codec.FrameCodec;
import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.Tier;
import com.acme.scaffold.kafka.gateway.bus.RecordSender;
import com.acme.scaffold.kafka.gateway.topology.TopicSpec;
import com.acme.scaffold.kafka.gateway.topology.Topology;
import com.acme.scaffold.kafka.rules.OrderingRules;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Kafka 分级投递总线（{@link MessageBus} 的生产实现）。
 *
 * <p>职责：取拓扑规格 → 编码信封 → 按业务键哈希选分区（同键 FIFO，§4.1）→
 * 经 {@link RecordSender} 端口发送（真实实现包裹 KafkaProducer；测试以 fake 注入）。
 * 双写非事务由调用方显式发起（§2.3）。
 *
 * <p>分区路由复用 {@link OrderingRules#partitionForKey}（与 Kafka 默认分区器 murmur2 行为
 * 位级一致，已在框架内统一实现，避免重复造哈希），不另行引入专用哈希器。
 *
 * <p>故障语义：{@link RecordSender#send} 返回 rejected 即视为 rejected，总线不抛异常，
 * 计数上报供对账（§9）。指标按通道聚合（BusMetrics 快照）。
 */
public final class KafkaBus implements MessageBus {

    private final Topology topology;
    private final RecordSender sender;

    private final AtomicLong produced = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final Map<Tier, AtomicLong> byTier = new EnumMap<>(Tier.class);

    public KafkaBus(Topology topology, RecordSender sender) {
        this.topology = topology;
        this.sender = sender;
        for (Tier t : Tier.values()) {
            byTier.put(t, new AtomicLong());
        }
    }

    @Override
    public ProduceResult produce(Envelope envelope, Tier tier) {
        TopicSpec spec = topology.specOf(tier);
        byte[] value = FrameCodec.encode(envelope);
        byte[] key = envelope.key() == null ? new byte[0] : envelope.key().getBytes(StandardCharsets.UTF_8);
        int partition = OrderingRules.partitionForKey(envelope.key(), spec.partitions());
        ProduceResult result = sender.send(tier, spec.name(), partition, key, value);
        if (result.accepted()) {
            produced.incrementAndGet();
        } else {
            rejected.incrementAndGet();
        }
        byTier.get(tier).incrementAndGet();
        return result;
    }

    @Override
    public void flush(long timeoutMs) {
        sender.flush(timeoutMs);
    }

    @Override
    public BusMetrics metrics() {
        Map<Tier, Long> snap = new EnumMap<>(Tier.class);
        byTier.forEach((t, c) -> snap.put(t, c.get()));
        return BusMetrics.of(produced.get(), rejected.get(), failed.get(), snap);
    }

    @Override
    public void close() {
        sender.close();
    }
}
