package com.acme.scaffold.realtime.gateway;

import com.acme.scaffold.common.messaging.bus.BusMetrics;
import com.acme.scaffold.common.messaging.bus.MessageBus;
import com.acme.scaffold.common.messaging.bus.ProduceResult;
import com.acme.scaffold.common.messaging.domain.Envelope;
import com.acme.scaffold.common.messaging.domain.Tier;

import java.util.ArrayList;
import java.util.List;

/** 测试用假总线：记录每次 produce 的 (信封, 通道)，并按配置返回接受/拒绝。 */
public final class FakeBus implements MessageBus {

    /** 一次生产记录。 */
    public static final class Produced {
        public final Envelope envelope;
        public final Tier tier;

        Produced(Envelope envelope, Tier tier) {
            this.envelope = envelope;
            this.tier = tier;
        }
    }

    private final List<Produced> produced = new ArrayList<>();
    private boolean flushCalled = false;
    private boolean closed = false;
    private final boolean accept;

    public FakeBus() {
        this(true);
    }

    public FakeBus(boolean accept) {
        this.accept = accept;
    }

    @Override
    public ProduceResult produce(Envelope envelope, Tier tier) {
        produced.add(new Produced(envelope, tier));
        return accept
                ? ProduceResult.accepted("t-" + tier, 0)
                : ProduceResult.rejected("t", "bus-down", "broker unreachable");
    }

    @Override
    public void flush(long timeoutMs) {
        flushCalled = true;
    }

    @Override
    public BusMetrics metrics() {
        return BusMetrics.zero();
    }

    @Override
    public void close() {
        closed = true;
    }

    public boolean flushCalled() {
        return flushCalled;
    }

    public boolean closed() {
        return closed;
    }

    public int count(Tier tier) {
        return (int) produced.stream().filter(p -> p.tier == tier).count();
    }

    public List<Produced> all() {
        return List.copyOf(produced);
    }
}
