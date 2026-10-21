package com.acme.scaffold.monitor.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LivenessProbeTest {

    @Test
    void check_returnsUpWithJvmDetails() {
        ProbeResult result = LivenessProbe.check();
        assertEquals(ProbeStatus.UP, result.status());
        assertEquals("ok", result.message());
        assertNotNull(result.details());
        assertTrue(result.details().containsKey("jvm"));
        assertEquals("alive", result.details().get("jvm"));
        assertTrue(result.details().containsKey("availableProcessors"));
        assertTrue(result.details().containsKey("uptimeMillis"));
    }

    @Test
    void check_doesNotDependOnExternalServices() {
        // 纯函数、无注入，连续调用应稳定返回 UP（不触发 OOM 临界分支）
        ProbeResult first = LivenessProbe.check();
        ProbeResult second = LivenessProbe.check();
        assertEquals(ProbeStatus.UP, first.status());
        assertEquals(first.status(), second.status());
    }
}
