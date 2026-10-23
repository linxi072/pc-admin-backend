package com.acme.scaffold.realtime.gateway.bus;

import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.MsgType;
import com.acme.scaffold.common.messaging.domain.Tier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link LocalBus} 兜底总线单测：计数语义 + 留存有界（防无 Kafka 长跑 OOM）。 */
@DisplayName("LocalBus 兜底总线")
class LocalBusTest {

    private static Envelope env(String msgId) {
        return new Envelope(0, msgId, MsgType.DATA, "k",
                msgId.getBytes(StandardCharsets.UTF_8), 1_700_000_000_000L, "");
    }

    @Test
    @DisplayName("produce：接受并计数，快照可见")
    void produce_acceptsAndCounts() {
        LocalBus bus = new LocalBus();
        bus.produce(env("a"), Tier.RT);
        bus.produce(env("b"), Tier.DURABLE);

        assertEquals(2, bus.metrics().produced());
        assertEquals(2, bus.snapshot().size());
    }

    @Test
    @DisplayName("留存有界：超出 MAX_RETAINED 后丢最旧，仅保留最近 N 条")
    void retention_bounded_dropsOldest() {
        LocalBus bus = new LocalBus();
        int total = LocalBus.MAX_RETAINED + 50;
        for (int i = 0; i < total; i++) {
            bus.produce(env("m" + i), Tier.RT);
        }

        assertEquals(LocalBus.MAX_RETAINED, bus.snapshot().size(), "快照不得突破留存上限");
        assertEquals(total, bus.metrics().produced(), "计数器仍累计全部生产量");
        var ids = bus.snapshot().stream().map(e -> new String(e.payload(), StandardCharsets.UTF_8)).toList();
        assertTrue(ids.contains("m" + (total - 1)), "必须保留最新一条");
        assertTrue(ids.contains("m" + (total - LocalBus.MAX_RETAINED)), "必须保留第 N 新的一条");
        assertTrue(!ids.contains("m0"), "最旧一条必须被丢弃");
    }

    @Test
    @DisplayName("reset：清空留存与计数（测试隔离）")
    void reset_clears() {
        LocalBus bus = new LocalBus();
        bus.produce(env("a"), Tier.RT);
        bus.reset();

        assertTrue(bus.snapshot().isEmpty());
        assertEquals(0, bus.metrics().produced());
    }
}
