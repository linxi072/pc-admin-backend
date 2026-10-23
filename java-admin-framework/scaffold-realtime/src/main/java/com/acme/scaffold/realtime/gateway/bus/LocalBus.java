package com.acme.scaffold.realtime.gateway.bus;

import com.acme.scaffold.common.messaging.bus.BusMetrics;
import com.acme.scaffold.common.messaging.bus.MessageBus;
import com.acme.scaffold.common.messaging.bus.ProduceResult;
import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.Tier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 无 broker 兜底总线（Kafka 网关关闭时的默认实现）。
 *
 * <p>使 {@link com.acme.scaffold.realtime.gateway.GatewayNode} 在「无 Kafka 集群」环境
 * （本地 / CI）仍可启动与全链路运行；双写语义被接受并计数，但消息不落外部存储
 * （跨节点扇出与持久化由启用 {@code kafka.gateway} 后的 {@code KafkaBus} 接管）。
 *
 * <p>与 wg-server 的演示版不同，本实现<b>不回推</b> RT 通道（避免「上行 → 总线 → 下行」自环），
 * 仅作内存留存 + 计数，供观测与单测快照。
 */
public final class LocalBus implements MessageBus {

    private final List<Envelope> records = new CopyOnWriteArrayList<>();
    private final AtomicLong produced = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    @Override
    public ProduceResult produce(Envelope envelope, Tier tier) {
        records.add(envelope);
        produced.incrementAndGet();
        return ProduceResult.accepted(tier.name(), -1);
    }

    @Override
    public void flush(long timeoutMs) {
        // 内存总线无刷盘语义
    }

    @Override
    public BusMetrics metrics() {
        return new BusMetrics(produced.get(), rejected.get(), 0L, Collections.emptyMap());
    }

    @Override
    public void close() {
        records.clear();
    }

    /** 快照当前留存信封（测试/观测用）。 */
    public List<Envelope> snapshot() {
        return List.copyOf(records);
    }

    /** 清空留存（测试隔离用）。 */
    public void reset() {
        records.clear();
        produced.set(0);
        rejected.set(0);
    }
}
