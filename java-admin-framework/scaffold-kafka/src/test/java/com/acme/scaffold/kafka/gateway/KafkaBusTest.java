package com.acme.scaffold.kafka.gateway;

import com.acme.scaffold.common.messaging.bus.BusMetrics;
import com.acme.scaffold.common.messaging.bus.ProduceResult;
import com.acme.scaffold.common.messaging.codec.FrameCodec;
import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.Tier;
import com.acme.scaffold.kafka.gateway.bus.RecordSender;
import com.acme.scaffold.kafka.gateway.topology.Topology;
import com.acme.scaffold.kafka.rules.OrderingRules;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KafkaBus} 分级投递总线测试（无 broker：以内存 fake {@link RecordSender} 注入）。
 *
 * <p>覆盖：produce 按通道选目标 topic 与分区（复用 OrderingRules 同 key 同分区）、
 * 编码后 value 等于 FrameCodec.encode、accepted/rejected 计数、metrics 快照、close 关闭底层资源。
 */
class KafkaBusTest {

    /** 内存发送器：捕获每次 send 调用并可控返回 accepted/rejected。 */
    static final class FakeSender implements RecordSender {
        final List<SendCall> calls = new ArrayList<>();
        boolean closed = false;
        boolean rejectNext = false;

        record SendCall(Tier tier, String topic, int partition, byte[] key, byte[] value) {
        }

        @Override
        public ProduceResult send(Tier tier, String topic, int partition, byte[] key, byte[] value) {
            calls.add(new SendCall(tier, topic, partition, key.clone(), value.clone()));
            if (rejectNext) {
                return ProduceResult.rejected(topic, "SEND_FAILED", "boom");
            }
            return ProduceResult.confirmed(topic, partition, calls.size());
        }

        @Override
        public void flush(long timeoutMs) {
            // no-op
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private Envelope envelope(String key) {
        return Envelope.downstream(1L, "m-" + key, key,
                "payload".getBytes(StandardCharsets.UTF_8), 1_700_000_000L, "");
    }

    @Test
    void produce_RT路由到rtTopic且分区由key决定() {
        FakeSender sender = new FakeSender();
        KafkaBus bus = new KafkaBus(Topology.designDefault(144, 64), sender);
        Envelope e = envelope("room-42");

        ProduceResult r = bus.produce(e, Tier.RT);

        assertTrue(r.accepted());
        assertEquals(1, sender.calls.size());
        var call = sender.calls.get(0);
        assertEquals(Tier.RT, call.tier());
        assertEquals("msg.raw.rt", call.topic());
        int expectedPartition = OrderingRules.partitionForKey(e.key(), 144);
        assertEquals(expectedPartition, call.partition());
        assertArrayEquals(e.key().getBytes(StandardCharsets.UTF_8), call.key);
        assertArrayEquals(FrameCodec.encode(e), call.value);
    }

    @Test
    void produce_DURABLE路由到durableTopic() {
        FakeSender sender = new FakeSender();
        KafkaBus bus = new KafkaBus(Topology.designDefault(144, 64), sender);
        Envelope e = envelope("device-7");

        bus.produce(e, Tier.DURABLE);

        assertEquals("msg.raw.durable", sender.calls.get(0).topic());
        assertEquals(Tier.DURABLE, sender.calls.get(0).tier());
    }

    @Test
    void produce_被拒计数rejected() {
        FakeSender sender = new FakeSender();
        sender.rejectNext = true;
        KafkaBus bus = new KafkaBus(Topology.designDefault(144, 64), sender);
        Envelope e = envelope("k1");

        ProduceResult r = bus.produce(e, Tier.RT);

        assertFalse(r.accepted());
        BusMetrics m = bus.metrics();
        assertEquals(1L, m.rejected());
        assertEquals(0L, m.produced());
        // 仍计入 byTier 计数（发送尝试发生）
        assertEquals(1L, m.byTier().get(Tier.RT));
    }

    @Test
    void metrics_聚合produced与byTier() {
        FakeSender sender = new FakeSender();
        KafkaBus bus = new KafkaBus(Topology.designDefault(144, 64), sender);
        bus.produce(envelope("a"), Tier.RT);
        bus.produce(envelope("b"), Tier.RT);
        bus.produce(envelope("c"), Tier.DURABLE);

        BusMetrics m = bus.metrics();
        assertEquals(3L, m.produced());
        assertEquals(2L, m.byTier().get(Tier.RT));
        assertEquals(1L, m.byTier().get(Tier.DURABLE));
    }

    @Test
    void close_关闭底层sender() {
        FakeSender sender = new FakeSender();
        KafkaBus bus = new KafkaBus(Topology.designDefault(144, 64), sender);
        bus.close();
        assertTrue(sender.closed);
    }
}
