package com.acme.scaffold.monitor.alert;

import java.time.Instant;

/**
 * 已接收告警的内存条目（收件箱），用于运维查看与潜在转发。不含敏感字段。
 */
public class AlertInboxEntry {

    private final String id;
    private final Instant receivedAt;
    private final String status;     // firing | resolved
    private final String name;       // labels.alertname
    private final String severity;   // labels.severity
    private final String summary;    // annotations.summary
    private final String instance;   // labels.instance
    private final String startsAt;
    private final String endsAt;

    public AlertInboxEntry(String id, Instant receivedAt, String status, String name,
                           String severity, String summary, String instance, String startsAt, String endsAt) {
        this.id = id;
        this.receivedAt = receivedAt;
        this.status = status;
        this.name = name;
        this.severity = severity;
        this.summary = summary;
        this.instance = instance;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public String getId() {
        return id;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    public String getSeverity() {
        return severity;
    }

    public String getSummary() {
        return summary;
    }

    public String getInstance() {
        return instance;
    }

    public String getStartsAt() {
        return startsAt;
    }

    public String getEndsAt() {
        return endsAt;
    }
}
