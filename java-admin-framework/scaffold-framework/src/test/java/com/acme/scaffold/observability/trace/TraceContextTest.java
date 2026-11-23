package com.acme.scaffold.observability.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * W3C traceparent 解析与生成契约单测（scaffold-framework 测试盲区补强）。
 * 纯函数、零 Spring 依赖，覆盖合法解析、非法降级、采样位与父子关系。
 */
class TraceContextTest {

    private static final String VALID = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    void shouldParseValidTraceparent() {
        Optional<TraceContext> ctx = TraceContext.parse(VALID);
        assertTrue(ctx.isPresent());
        assertEquals("01", ctx.get().getTraceFlags(), "采样位为 01");
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", ctx.get().getTraceId());
        assertEquals("00f067aa0ba902b7", ctx.get().getSpanId());
        assertTrue(ctx.get().isSampled());
        assertEquals(VALID, ctx.get().toTraceparent());
    }

    @Test
    void unSampledFlagShouldReportFalse() {
        String header = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-00";
        TraceContext ctx = TraceContext.parse(header).orElseThrow();
        assertFalse(ctx.isSampled());
    }

    @Test
    void malformedHeadersShouldBeRejected() {
        String[] bad = {
                null, "",
                "01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", // 未知版本
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7",       // 段数不足
                "00-00000000000000000000000000000000-00f067aa0ba902b7-01",   // 全零 traceId
                "00-4bf92f3577b34da6a3ce929d0e0e4736-0000000000000000-01",   // 全零 spanId
                "00-4bf92f3577b34da6a3ce929d0e0e473z-00f067aa0ba902b7-01",   // 非 hex
                "00-4bf9-00f067aa0ba902b7-01"                                 // traceId 长度不足
        };
        for (String header : bad) {
            assertTrue(TraceContext.parse(header).isEmpty(), "应拒绝非法头: " + header);
        }
    }

    @Test
    void rootAndChildShouldBeValidAndShareTrace() {
        TraceContext root = TraceContext.root();
        assertEquals(32, root.getTraceId().length());
        assertEquals(16, root.getSpanId().length());
        assertTrue(root.isSampled());

        TraceContext child = TraceContext.childOf(root);
        assertEquals(root.getTraceId(), child.getTraceId(), "子 span 沿用同一 traceId");
        assertEquals(root.getTraceFlags(), child.getTraceFlags(), "子 span 沿用采样位");
        assertFalse(root.getSpanId().equals(child.getSpanId()), "子 span 使用新 spanId");
    }
}
