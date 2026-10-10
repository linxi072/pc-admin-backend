package com.acme.scaffold.observability.export;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;

/**
 * 请求级 span 导出过滤器：在 {@code TraceIdFilter}（HIGHEST_PRECEDENCE）之后运行，
 * 为每个 HTTP 请求生成一个 server span 并经由 {@link OtlpSpanExporter} 导出（默认关闭）。
 *
 * <p>span 的 traceId 复用 MDC {@code traceId}（与 W3C {@code traceparent} / 审计日志 / 全局异常处理器一致），
 * 满足「日志与 span 可双向跳转」。关闭状态下直接透传，零开销。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OtlpTracingFilter extends OncePerRequestFilter {

    private static final String MDC_TRACE_ID = "traceId";
    private static final SecureRandom RNG = new SecureRandom();

    private final SpanExporter exporter;

    public OtlpTracingFilter(SpanExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!exporter.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }
        String traceId = MDC.get(MDC_TRACE_ID);
        if (traceId == null) {
            traceId = randomHex(32);
        }
        String spanId = randomHex(16);

        long t0 = System.nanoTime();
        long epochStartMs = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long t1 = System.nanoTime();
            long startEpochNanos = epochStartMs * 1_000_000L;
            long endEpochNanos = startEpochNanos + (t1 - t0);
            int status = response.getStatus();
            OtlpSpan.StatusCode statusCode = status >= 500 ? OtlpSpan.StatusCode.ERROR : OtlpSpan.StatusCode.OK;
            OtlpSpan span = new OtlpSpan(
                    traceId, spanId, null,
                    request.getMethod() + " " + safePath(request),
                    OtlpSpan.Kind.SERVER, startEpochNanos, endEpochNanos,
                    List.of(
                            new OtlpSpan.Attribute("http.method", request.getMethod()),
                            new OtlpSpan.Attribute("http.route", safePath(request)),
                            new OtlpSpan.Attribute("http.status_code", String.valueOf(status))
                    ),
                    statusCode, status >= 500 ? "server error" : null);
            exporter.export(List.of(span));
        }
    }

    private static String safePath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null ? "/" : uri;
    }

    private static String randomHex(int len) {
        byte[] bytes = new byte[len / 2];
        RNG.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
