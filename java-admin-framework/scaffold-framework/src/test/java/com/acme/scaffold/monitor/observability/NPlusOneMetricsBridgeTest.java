package com.acme.scaffold.monitor.observability;

import com.acme.scaffold.monitor.detection.NPlusOneSuspectRegistry;
import com.acme.scaffold.observability.metrics.MetricsRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NPlusOneMetricsBridgeTest {

    @Test
    void registersGaugesSamplingLiveRegistry() {
        var meterRegistry = new SimpleMeterRegistry();
        var suspectRegistry = new NPlusOneSuspectRegistry(10);
        var metrics = new MetricsRegistry(meterRegistry);

        new NPlusOneMetricsBridge(metrics, suspectRegistry).register();

        assertEquals(0.0, meterRegistry.get("app.monitor.nplus1.total").gauge().value());
        assertEquals(0.0, meterRegistry.get("app.monitor.nplus1.retained").gauge().value());
        assertEquals(10.0, meterRegistry.get("app.monitor.nplus1.capacity").gauge().value());

        suspectRegistry.record("fp:selectUser", "select * from sys_user where id = ?", 12);
        suspectRegistry.record("fp:selectUser", "select * from sys_user where id = ?", 15);

        assertEquals(2.0, meterRegistry.get("app.monitor.nplus1.total").gauge().value());
        assertEquals(2.0, meterRegistry.get("app.monitor.nplus1.retained").gauge().value());
        assertEquals(10.0, meterRegistry.get("app.monitor.nplus1.capacity").gauge().value());
    }
}
