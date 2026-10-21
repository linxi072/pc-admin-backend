package com.acme.scaffold.observability.export;

import com.acme.scaffold.observability.recorder.SpanRecorder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtlpTracingFilterTest {

    @Test
    void exportsSpanWithMdcTraceId() throws Exception {
        RecordingExporter recording = new RecordingExporter(true);
        SpanRecorder recorder = new SpanRecorder(new ObservabilityProperties());
        OtlpTracingFilter filter = new OtlpTracingFilter(List.of(recording, recorder));

        MDC.put("traceId", "abc123");
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn("/api/users");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertEquals(1, recording.spans.size());
        assertEquals("abc123", recording.spans.get(0).traceId);
        assertEquals(OtlpSpan.Kind.SERVER, recording.spans.get(0).kind);
        assertEquals("200", recording.spans.get(0).attributes.get(2).value);
        // 导出与本地记录正交：两条链路均应消费同一条 span
        assertEquals(1, recorder.size());
        MDC.remove("traceId");
    }

    @Test
    void skipsWhenBothDisabled() throws Exception {
        RecordingExporter recording = new RecordingExporter(false);
        SpanRecorder recorder = disabledRecorder();
        OtlpTracingFilter filter = new OtlpTracingFilter(List.of(recording, recorder));

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn("/x");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertTrue(recording.spans.isEmpty());
        assertEquals(0, recorder.size());
    }

    @Test
    void recordsLocallyWhenExporterDisabled() throws Exception {
        // 本地记录开启、Collector 导出关闭：span 仍应进入本地缓冲（本地回溯价值所在）
        RecordingExporter recording = new RecordingExporter(false);
        SpanRecorder recorder = new SpanRecorder(new ObservabilityProperties());
        OtlpTracingFilter filter = new OtlpTracingFilter(List.of(recording, recorder));

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("POST");
        when(req.getRequestURI()).thenReturn("/api/login");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(500);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertTrue(recording.spans.isEmpty());
        List<OtlpSpan> spans = recorder.recent(10);
        assertEquals(1, spans.size());
        assertEquals(OtlpSpan.StatusCode.ERROR, spans.get(0).status);
        assertEquals("POST /api/login", spans.get(0).name);
        assertFalse(spans.get(0).traceId.isBlank());
    }

    private static SpanRecorder disabledRecorder() {
        ObservabilityProperties props = new ObservabilityProperties();
        props.getRecorder().setEnabled(false);
        return new SpanRecorder(props);
    }

    static class RecordingExporter implements SpanExporter {
        final boolean enabled;
        final List<OtlpSpan> spans = new ArrayList<>();

        RecordingExporter(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void export(List<OtlpSpan> s) {
            spans.addAll(s);
        }
    }
}
