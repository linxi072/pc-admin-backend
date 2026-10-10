package com.acme.scaffold.config;

import com.acme.scaffold.observability.TraceContext;
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
import java.util.Optional;

/**
 * W3C Trace Context 传播过滤器（同时向后兼容 X-Trace-Id）。
 *
 * <p>每个请求绑定唯一 traceId 到 MDC（{@code traceId}），与审计日志、Result.traceId、
 * 全局异常处理器共用同一链路 ID，满足「日志与 span 可双向跳转」。
 * 解析入站 {@code traceparent} 头延续上游链路；无则开启新根链路。
 * 响应头写回 {@code traceparent}（W3C 标准）与 {@code X-Trace-Id}（历史兼容，值同 W3C traceId）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String MDC_TRACE_ID = "traceId";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Optional<TraceContext> parsed = TraceContext.parse(request.getHeader(TraceContext.HEADER_TRACEPARENT));
        // 延续上游 trace：本服务作为 server span 生成新 spanId；无上游则开启新根链路
        TraceContext ctx = parsed.map(TraceContext::childOf).orElseGet(TraceContext::root);

        MDC.put(MDC_TRACE_ID, ctx.getTraceId());
        try {
            response.setHeader(TraceContext.HEADER_TRACEPARENT, ctx.toTraceparent());
            response.setHeader(HEADER_TRACE_ID, ctx.getTraceId());
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }
}
