package com.acme.scaffold.monitor.alert;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class AlertReceiverServiceTest {

    private AlertReceiverService newService() {
        AlertProperties p = new AlertProperties();
        p.setInboxCapacity(10);
        return new AlertReceiverService(p, mock(ApplicationEventPublisher.class));
    }

    private AlertPayload sample() {
        AlertPayload p = new AlertPayload();
        p.status = "firing";
        AlertPayload.Alert a = new AlertPayload.Alert();
        a.status = "firing";
        a.labels = Map.of("alertname", "HighErrorRate", "severity", "critical", "instance", "localhost:8080");
        a.annotations = Map.of("summary", "error rate > 1%");
        a.startsAt = "2026-01-01T00:00:00Z";
        a.endsAt = "2026-01-01T00:05:00Z";
        p.alerts = List.of(a);
        return p;
    }

    @Test
    void receive_storesAndCounts() {
        AlertReceiverService s = newService();
        int n = s.receive(sample());
        assertEquals(1, n);
        assertEquals(1, s.inboxSize());
        AlertInboxEntry e = s.inbox().get(0);
        assertEquals("HighErrorRate", e.getName());
        assertEquals("critical", e.getSeverity());
        assertEquals("firing", e.getStatus());
        assertEquals("error rate > 1%", e.getSummary());
        assertEquals("localhost:8080", e.getInstance());
    }

    @Test
    void receive_nullOrEmpty_returnsZero() {
        AlertReceiverService s = newService();
        assertEquals(0, s.receive(null));
        assertEquals(0, s.receive(new AlertPayload()));
    }

    @Test
    void capacityTrimKeepsMostRecent() {
        AlertProperties p = new AlertProperties();
        p.setInboxCapacity(2);
        AlertReceiverService s = new AlertReceiverService(p, mock(ApplicationEventPublisher.class));
        for (int i = 0; i < 5; i++) {
            AlertPayload payload = new AlertPayload();
            AlertPayload.Alert a = new AlertPayload.Alert();
            a.status = "firing";
            a.labels = Map.of("alertname", "A" + i);
            payload.alerts = List.of(a);
            s.receive(payload);
        }
        assertEquals(2, s.inboxSize());
        // 保留最近两条：A4、A3（addFirst，最新在队首）
        assertEquals("A4", s.inbox().get(0).getName());
        assertEquals("A3", s.inbox().get(1).getName());
    }
}
