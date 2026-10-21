package com.acme.scaffold.observability.export;

import com.acme.scaffold.config.web.TraceIdFilter;
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
 * 请求级 span 采集过滤器：在 {@code TraceIdFilter}（HIGHEST_PRECEDENCE）之后运行，
 * 为每个 HTTP 请求生成一个 server span，并分发给全部已启用的 {@link SpanExporter} sink
 * （本地留存记录器 + OTLP Collector 导出器，二者开关正交、可独立开启）。
 *
 * <p>span 的 traceId 复用 MDC {@code traceId}（与 W3C {@code traceparent} / 审计日志 / 全局异常处理器一致），
 * 满足「日志与 span 可双向跳转」。所有 sink 均关闭时直接透传，零开销；任一开启即构造 span，
 * 并按各 sink 自身开关分发（本地留存开启时无需 Collector 亦可回溯追踪）。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class OtlpTracingFilter extends OncePerRequestFilter {

    private static final String MDC_TRACE_ID = "traceId";
    private static final SecureRandom RNG = new SecureRandom();

    /** 全部 span sink（Spring 按 bean 聚合注入：本地留存记录器 + OTLP 导出器）。 */
    private final List<SpanExporter> exporters;

    public OtlpTracingFilter(List<SpanExporter> exporters) {
        this.exporters = exporters;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        boolean anyEnabled = exporters.stream().anyMatch(SpanExporter::isEnabled);
        if (!anyEnabled) {
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
            List<OtlpSpan> batch = List.of(span);
            for (SpanExporter exporter : exporters) {
                if (exporter.isEnabled()) {
                    exporter.export(batch);
                }
            }
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
