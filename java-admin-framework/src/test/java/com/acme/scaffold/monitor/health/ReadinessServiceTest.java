package com.acme.scaffold.monitor.health;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReadinessServiceTest {

    @Test
    void check_databaseUp_returnsUp() {
        ReadinessService service = new ReadinessService(() -> true);
        ProbeResult result = service.check();
        assertEquals(ProbeStatus.UP, result.status());
        assertEquals("up", result.details().get("database"));
    }

    @Test
    void check_databaseDown_returnsDown() {
        ReadinessService service = new ReadinessService(() -> false);
        ProbeResult result = service.check();
        assertEquals(ProbeStatus.DOWN, result.status());
        assertEquals("database unavailable", result.message());
        assertEquals("down", result.details().get("database"));
    }
}
