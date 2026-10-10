package com.acme.scaffold.observability.export;

import java.util.List;

/**
 * 与 OTLP 对齐的 span 模型。纯数据结构，无 Spring / Servlet 依赖，便于在单测中脱离容器验证。
 *
 * <p>字段语义对应 OpenTelemetry span：traceId（32 hex）、spanId（16 hex）、parentSpanId（可空）、
 * 名称、种类（SERVER/CLIENT/...）、起止时间（epoch 纳秒）、属性键值对、状态。
 */
public final class OtlpSpan {

    public final String traceId;
    public final String spanId;
    public final String parentSpanId;
    public final String name;
    public final Kind kind;
    public final long startEpochNanos;
    public final long endEpochNanos;
    public final List<Attribute> attributes;
    public final StatusCode status;
    public final String statusMessage;

    public OtlpSpan(String traceId, String spanId, String parentSpanId, String name, Kind kind,
                   long startEpochNanos, long endEpochNanos, List<Attribute> attributes,
                   StatusCode status, String statusMessage) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.name = name;
        this.kind = kind;
        this.startEpochNanos = startEpochNanos;
        this.endEpochNanos = endEpochNanos;
        this.attributes = attributes;
        this.status = status;
        this.statusMessage = statusMessage;
    }

    public long durationNanos() {
        return endEpochNanos - startEpochNanos;
    }

    public enum Kind {
        INTERNAL, SERVER, CLIENT, PRODUCER, CONSUMER
    }

    public enum StatusCode {
        UNSET, OK, ERROR
    }

    public static final class Attribute {
        public final String key;
        public final String value;

        public Attribute(String key, String value) {
            this.key = key;
            this.value = value;
        }
    }
}
