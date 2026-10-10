package com.acme.scaffold.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsRegistryTest {

    @Test
    void counter_incrementsAndCarriesTags() {
        MetricsRegistry m = new MetricsRegistry(new SimpleMeterRegistry());
        var c = m.counter("app.test.counter", "测试计数器", "env", "test");
        c.increment();
        c.increment();
        assertEquals(2.0, c.count());
        assertTrue(c.getId().getTags().stream()
                .anyMatch(t -> t.getKey().equals("env") && t.getValue().equals("test")));
    }

    @Test
    void timer_recordsDuration() {
        MetricsRegistry m = new MetricsRegistry(new SimpleMeterRegistry());
        var t = m.timer("app.test.timer", "测试计时器");
        t.record(100, TimeUnit.MILLISECONDS);
        assertEquals(1, t.count());
    }

    @Test
    void summary_recordsValue() {
        MetricsRegistry m = new MetricsRegistry(new SimpleMeterRegistry());
        var s = m.summary("app.test.summary", "测试摘要");
        s.record(42.0);
        assertEquals(42.0, s.totalAmount());
    }

    @Test
    void gauge_samplesState() {
        MetricsRegistry m = new MetricsRegistry(new SimpleMeterRegistry());
        var holder = new double[]{5.0};
        var g = m.gauge("app.test.gauge", "测试仪表", holder, arr -> arr[0]);
        assertEquals(5.0, g.value());
        holder[0] = 9.0;
        assertEquals(9.0, g.value());
    }

    @Test
    void oddTagsRejected() {
        MetricsRegistry m = new MetricsRegistry(new SimpleMeterRegistry());
        assertThrows(IllegalArgumentException.class,
                () -> m.counter("x", "d", "only-one"));
    }
}
