package com.acme.scaffold.observability;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;

/**
 * W3C Trace Context（{@code traceparent}）解析与生成。
 *
 * <p>纯函数实现，不依赖 Spring / Servlet，可在单测中脱离容器验证。采用 W3C 推荐格式（版本 00）：
 * <pre>
 *   version-trace-id-span-id-trace-flags
 *   00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
 * </pre>
 * 该格式与 OpenTelemetry 完全兼容：后续引入 OTel SDK（{@code -Potel}）时，现有 traceId / spanId
 * 可由 SDK 直接接管并导出至 Collector（Jaeger / Tempo），业务代码无需改造。
 *
 * <p>设计约束（W3C 规范）：trace-id 与 span-id 须为随机值且不全 0；未知版本号按规范应透传，
 * 本类仅识别 00（其余视为非法返回 {@link Optional#empty()}，由过滤器降级处理）。
 */
public final class TraceContext {

    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String SUPPORTED_VERSION = "00";

    private static final int TRACE_ID_HEX_LEN = 32;
    private static final int SPAN_ID_HEX_LEN = 16;
    private static final int FLAGS_HEX_LEN = 2;
    private static final SecureRandom RNG = new SecureRandom();

    private final String version;
    private final String traceId;
    private final String spanId;
    private final String traceFlags;

    private TraceContext(String version, String traceId, String spanId, String traceFlags) {
        this.version = version;
        this.traceId = traceId;
        this.spanId = spanId;
        this.traceFlags = traceFlags;
    }

    /** 生成新的根 trace（无上游上下文时）。采样位默认置 1。 */
    public static TraceContext root() {
        return new TraceContext(SUPPORTED_VERSION, randomHex(TRACE_ID_HEX_LEN),
                randomHex(SPAN_ID_HEX_LEN), "01");
    }

    /** 基于上游上下文生成本服务的 server span：沿用同一 traceId 与采样位，新生成 spanId。 */
    public static TraceContext childOf(TraceContext parent) {
        return new TraceContext(parent.version, parent.traceId,
                randomHex(SPAN_ID_HEX_LEN), parent.traceFlags);
    }

    /**
     * 解析 W3C traceparent 头。非法（格式/长度/全 0/非 hex/非 00 版本）返回 {@link Optional#empty()}。
     * 注意：未知版本按 W3C 规范应透传，本实现仅支持 00，由调用方决定降级策略。
     */
    public static Optional<TraceContext> parse(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        String[] parts = header.split("-");
        if (parts.length != 4) {
            return Optional.empty();
        }
        String version = parts[0];
        if (!SUPPORTED_VERSION.equals(version)) {
            return Optional.empty();
        }
        String traceId = parts[1];
        String spanId = parts[2];
        String flags = parts[3];
        if (!isHex(traceId, TRACE_ID_HEX_LEN) || isAllZero(traceId)) {
            return Optional.empty();
        }
        if (!isHex(spanId, SPAN_ID_HEX_LEN) || isAllZero(spanId)) {
            return Optional.empty();
        }
        if (!isHex(flags, FLAGS_HEX_LEN)) {
            return Optional.empty();
        }
        return Optional.of(new TraceContext(version, traceId, spanId, flags));
    }

    /** 标准 W3C traceparent 头取值。 */
    public String toTraceparent() {
        return version + "-" + traceId + "-" + spanId + "-" + traceFlags;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getTraceFlags() {
        return traceFlags;
    }

    /** 是否采样（trace-flags 第 0 位 = 1）。 */
    public boolean isSampled() {
        int b = Integer.parseUnsignedInt(traceFlags, 16);
        return (b & 0x1) == 0x1;
    }

    private static String randomHex(int len) {
        byte[] bytes = new byte[len / 2];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static boolean isHex(String s, int len) {
        if (s == null || s.length() != len) {
            return false;
        }
        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAllZero(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }
}
