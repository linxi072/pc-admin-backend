package com.acme.scaffold.monitor.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 告警接收服务：接收 Alertmanager webhook 推送，入库内存收件箱并发布 {@link AlertReceivedEvent}，
 * 供后续转发/通知使用。内存收件箱按 {@link AlertProperties#getInboxCapacity()} 环形裁剪。
 */
@Service
public class AlertReceiverService {

    private static final Logger log = LoggerFactory.getLogger(AlertReceiverService.class);

    private final int capacity;
    private final ApplicationEventPublisher eventPublisher;
    private final ConcurrentLinkedDeque<AlertInboxEntry> inbox = new ConcurrentLinkedDeque<>();

    public AlertReceiverService(AlertProperties props, ApplicationEventPublisher eventPublisher) {
        this.capacity = props.getInboxCapacity();
        this.eventPublisher = eventPublisher;
    }

    /**
     * 接收 Alertmanager webhook 推送的告警，返回实际入箱的告警条数。
     * payload 为 null 或 alerts 为空时返回 0（不入库）。
     */
    public int receive(AlertPayload payload) {
        if (payload == null || payload.alerts == null || payload.alerts.isEmpty()) {
            return 0;
        }
        List<AlertInboxEntry> entries = new ArrayList<>(payload.alerts.size());
        for (AlertPayload.Alert a : payload.alerts) {
            AlertInboxEntry e = toEntry(a);
            inbox.addFirst(e);
            entries.add(e);
            log.info("ALERT received status={} name={} severity={} summary={}",
                    e.getStatus(), e.getName(), e.getSeverity(), e.getSummary());
        }
        while (inbox.size() > capacity) {
            inbox.pollLast();
        }
        if (!entries.isEmpty()) {
            eventPublisher.publishEvent(new AlertReceivedEvent(this, entries));
        }
        return entries.size();
    }

    public List<AlertInboxEntry> inbox() {
        return new ArrayList<>(inbox);
    }

    public int inboxSize() {
        return inbox.size();
    }

    private AlertInboxEntry toEntry(AlertPayload.Alert a) {
        String name = a.labels != null ? a.labels.get("alertname") : null;
        String severity = a.labels != null ? a.labels.get("severity") : null;
        String instance = a.labels != null ? a.labels.get("instance") : null;
        String summary = a.annotations != null ? a.annotations.get("summary") : null;
        return new AlertInboxEntry(
                UUID.randomUUID().toString(),
                Instant.now(),
                a.status,
                name, severity, summary, instance,
                a.startsAt, a.endsAt);
    }
}
