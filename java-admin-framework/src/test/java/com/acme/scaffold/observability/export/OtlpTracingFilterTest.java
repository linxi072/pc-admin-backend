package com.acme.scaffold.observability.export;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OtlpTracingFilterTest {

    @Test
    void exportsSpanWithMdcTraceId() throws Exception {
        RecordingExporter recording = new RecordingExporter(true);
        OtlpTracingFilter filter = new OtlpTracingFilter(recording);

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
        MDC.remove("traceId");
    }

    @Test
    void skipsWhenDisabled() throws Exception {
        RecordingExporter recording = new RecordingExporter(false);
        OtlpTracingFilter filter = new OtlpTracingFilter(recording);

        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getMethod()).thenReturn("GET");
        when(req.getRequestURI()).thenReturn("/x");
        HttpServletResponse resp = mock(HttpServletResponse.class);
        when(resp.getStatus()).thenReturn(200);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(req, resp, chain);

        assertTrue(recording.spans.isEmpty());
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
