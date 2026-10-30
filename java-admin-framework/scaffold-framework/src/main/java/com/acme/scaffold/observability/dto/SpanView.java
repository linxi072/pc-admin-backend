package com.acme.scaffold.observability.dto;

import java.util.Map;

/**
 * span 只读视图（对应 OTLP server span，时间字段折算为毫秒便于前端直接展示）。
 *
 * @param traceId        追踪标识（32 hex，与日志 MDC traceId 一致，可双向跳转）
 * @param spanId         本 span 标识（16 hex）
 * @param parentSpanId   父 span 标识（server span 无父时为 null）
 * @param name           span 名称（如 "GET /api/users"）
 * @param kind           种类（SERVER 等，取 OTLP 枚举名）
 * @param status         状态（UNSET / OK / ERROR）
 * @param statusMessage  状态补充说明（ERROR 时的原因，可为 null）
 * @param startEpochMillis 开始时间（epoch 毫秒）
 * @param durationMillis   耗时（毫秒）
 * @param attributes     属性键值对（http.method / http.route / http.status_code 等）
 */
public record SpanView(String traceId, String spanId, String parentSpanId, String name,
                       String kind, String status, String statusMessage,
                       long startEpochMillis, long durationMillis, Map<String, String> attributes) {
}
