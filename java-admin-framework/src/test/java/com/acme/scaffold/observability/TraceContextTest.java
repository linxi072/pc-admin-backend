package com.acme.scaffold.observability;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * W3C Trace Context 纯函数单测（G1）。
 * 不依赖 Spring / Servlet，离线即可运行，验证解析 / 生成 / 子 span / 采样位。
 */
class TraceContextTest {

    @Test
    void root_generatesValidW3CHeader() {
        TraceContext ctx = TraceContext.root();
        assertTrue(ctx.getTraceId().matches("[0-9a-f]{32}"), "traceId 应为 32 位十六进制");
        assertTrue(ctx.getSpanId().matches("[0-9a-f]{16}"), "spanId 应为 16 位十六进制");
        assertEquals("00", ctx.toTraceparent().split("-")[0]);
        assertTrue(ctx.isSampled(), "根 span 默认采样");
    }

    @Test
    void childOf_keepsTraceIdAndNewSpan() {
        TraceContext parent = TraceContext.root();
        TraceContext child = TraceContext.childOf(parent);
        assertEquals(parent.getTraceId(), child.getTraceId(), "子 span 应沿用父 traceId");
        assertNotEquals(parent.getSpanId(), child.getSpanId(), "子 span 应生成新 spanId");
        assertEquals(parent.getTraceFlags(), child.getTraceFlags());
    }

    @Test
    void parse_validHeader_succeeds() {
        TraceContext parent = TraceContext.root();
        String header = parent.toTraceparent();
        Optional<TraceContext> parsed = TraceContext.parse(header);
        assertTrue(parsed.isPresent());
        assertEquals(parent.getTraceId(), parsed.get().getTraceId());
        assertEquals(parent.getSpanId(), parsed.get().getSpanId());
        assertEquals(parent.getTraceFlags(), parsed.get().getTraceFlags());
    }

    @Test
    void parse_nullOrBlank_isEmpty() {
        assertTrue(TraceContext.parse(null).isEmpty());
        assertTrue(TraceContext.parse("").isEmpty());
        assertTrue(TraceContext.parse("   ").isEmpty());
    }

    @Test
    void parse_malformedFormat_isEmpty() {
        assertTrue(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7").isEmpty(), "缺少 flags");
        assertTrue(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01-extra").isEmpty(), "字段过多");
        assertTrue(TraceContext.parse("4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").isEmpty(), "缺少 version");
    }

    @Test
    void parse_wrongVersion_isEmpty() {
        assertTrue(TraceContext.parse("ff-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").isEmpty());
    }

    @Test
    void parse_nonHexOrAllZero_isEmpty() {
        assertTrue(TraceContext.parse("00-00000000000000000000000000000000-00f067aa0ba902b7-01").isEmpty(), "traceId 全 0");
        assertTrue(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01").isEmpty(), "spanId 全 0");
        assertTrue(TraceContext.parse("00-zz992f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").isEmpty(), "非法 hex");
        assertTrue(TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-xyz").isEmpty(), "flags 非法 hex");
    }

    @Test
    void isSampled_reflectsFlags() {
        TraceContext sampled = TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01").orElseThrow();
        assertTrue(sampled.isSampled());
        TraceContext notSampled = TraceContext.parse("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00").orElseThrow();
        assertFalse(notSampled.isSampled());
    }

    @Test
    void traceIdRandomness_uniqueAcrossRoots() {
        assertNotEquals(TraceContext.root().getTraceId(), TraceContext.root().getTraceId());
    }
}
