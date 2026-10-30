package com.acme.scaffold.observability.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * OTLP/HTTP v1 JSON 序列化（纯函数，无 Spring 依赖）。
 *
 * <p>生成结构：{@code resourceSpans[].resource.attributes[].(service.name) + scopeSpans[].spans[]}，
 * 与 OpenTelemetry Collector / Jaeger / Tempo 的 OTLP/HTTP 接收协议兼容。
 */
public final class OtlpSpanSerializer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OtlpSpanSerializer() {
    }

    public static String toJson(List<OtlpSpan> spans, String serviceName) {
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode resourceSpans = root.putArray("resourceSpans");
        ObjectNode rs = resourceSpans.addObject();

        ObjectNode resource = rs.putObject("resource");
        ArrayNode attrs = resource.putArray("attributes");
        ObjectNode svc = attrs.addObject();
        svc.put("key", "service.name");
        svc.putObject("value").put("stringValue", serviceName);

        ObjectNode scopeSpans = rs.putArray("scopeSpans").addObject();
        scopeSpans.putObject("scope").put("name", "java-admin-framework");
        ArrayNode out = scopeSpans.putArray("spans");
        for (OtlpSpan s : spans) {
            out.add(spanNode(s));
        }
        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("serialize otlp spans failed", e);
        }
    }

    private static ObjectNode spanNode(OtlpSpan s) {
        ObjectNode n = MAPPER.createObjectNode();
        n.put("traceId", s.traceId);
        n.put("spanId", s.spanId);
        if (s.parentSpanId != null) {
            n.put("parentSpanId", s.parentSpanId);
        }
        n.put("name", s.name);
        n.put("kind", kindCode(s.kind));
        n.put("startTimeUnixNano", Long.toString(s.startEpochNanos));
        n.put("endTimeUnixNano", Long.toString(s.endEpochNanos));
        if (s.attributes != null && !s.attributes.isEmpty()) {
            ArrayNode a = n.putArray("attributes");
            for (OtlpSpan.Attribute at : s.attributes) {
                ObjectNode o = a.addObject();
                o.put("key", at.key);
                o.putObject("value").put("stringValue", at.value);
            }
        }
        ObjectNode st = n.putObject("status");
        st.put("code", statusCode(s.status));
        if (s.statusMessage != null) {
            st.put("message", s.statusMessage);
        }
        return n;
    }

    // OTLP span kind: INTERNAL=1, SERVER=2, CLIENT=3, PRODUCER=4, CONSUMER=5
    private static int kindCode(OtlpSpan.Kind k) {
        return switch (k) {
            case INTERNAL -> 1;
            case SERVER -> 2;
            case CLIENT -> 3;
            case PRODUCER -> 4;
            case CONSUMER -> 5;
        };
    }

    // OTLP status code: UNSET=0, OK=1, ERROR=2
    private static int statusCode(OtlpSpan.StatusCode c) {
        return switch (c) {
            case UNSET -> 0;
            case OK -> 1;
            case ERROR -> 2;
        };
    }
}
