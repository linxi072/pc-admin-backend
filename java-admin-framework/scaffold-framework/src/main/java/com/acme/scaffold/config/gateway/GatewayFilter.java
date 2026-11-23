package com.acme.scaffold.config.gateway;

import com.acme.scaffold.common.web.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 轻量网关前置过滤器（方向 B / B3）：在业务链路前统一做「内网准入 + 全局限流 + CORS 预检终结」。
 *
 * <p><b>零依赖自研</b>：不引入 Spring Cloud Gateway，复用 Servlet 过滤器链 + 进程内计数，
 * 与 {@code CircuitBreaker}/{@code OtlpExporter} 同风格。限流器为实例内建（framework 不依赖 security
 * 模块，避免破坏依赖 DAG），实现固定窗口计数，口径与 {@code InMemoryRateLimiter} 一致。</p>
 *
 * <p><b>安全护栏</b>：仅当 {@code gateway.enabled=true} 时注册本 Bean（由 {@link GatewayAutoConfiguration}
 * 门控）；关闭时不存在于链路，零副作用。默认 fail-closed：限流器异常时拒绝请求。</p>
 *
 * <p><b>排序</b>：{@code HIGHEST_PRECEDENCE + 10}，紧跟 TraceIdFilter（HIGHEST_PRECEDENCE）之后，
 * 保证 429/403 短路响应也携带 traceId；在 Spring Security 鉴权链之前，先于业务被拦截。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class GatewayFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayFilter.class);

    private final GatewayProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final GatewayRateLimiter rateLimiter;

    public GatewayFilter(GatewayProperties properties, ClientIpResolver clientIpResolver) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = new GatewayRateLimiter();
    }

    /** 供测试注入限流器。 */
    GatewayFilter(GatewayProperties properties, ClientIpResolver clientIpResolver, GatewayRateLimiter rateLimiter) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
        this.rateLimiter = rateLimiter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String clientIp = clientIpResolver.resolve(request);

        // 1) actuator 内网准入（fail-closed）
        if (!GatewayRouteRules.allowActuator(uri, clientIp, properties.getInternalCidrs())) {
            log.warn("网关拒绝访问运维端点：uri={} clientIp={}", uri, clientIp);
            writeReject(response, HttpServletResponse.SC_FORBIDDEN, "GATEWAY_ACTUATOR_FORBIDDEN",
                    "运维端点仅允许内网访问");
            return;
        }

        // 2) CORS 预检终结：合法来源的 OPTIONS 由网关直接回 204，不转发业务
        if (GatewayCorsRules.isPreflight(request.getMethod(),
                request.getHeader(GatewayCorsRules.REQUEST_METHOD_HEADER))) {
            if (!properties.getAllowedOrigins().isEmpty()) {
                String allowOrigin = GatewayCorsRules.allowOriginValue(
                        request.getHeader("Origin"), properties.getAllowedOrigins());
                if (allowOrigin == null) {
                    log.warn("网关拒绝 CORS 预检：origin={} uri={}", request.getHeader("Origin"), uri);
                    writeReject(response, HttpServletResponse.SC_FORBIDDEN, "GATEWAY_CORS_FORBIDDEN",
                            "来源不在允许列表");
                    return;
                }
                response.setHeader(GatewayCorsRules.ALLOW_ORIGIN_HEADER, allowOrigin);
                response.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type,X-Request-Id,X-Request-Timestamp,X-Request-Nonce");
                response.setHeader("Access-Control-Max-Age", "3600");
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
        }

        // 3) 非白名单来源的跨域请求：拒绝（配置 CORS 白名单后生效）
        if (!properties.getAllowedOrigins().isEmpty()) {
            String origin = request.getHeader("Origin");
            if (origin != null && !origin.isBlank()) {
                String allowOrigin = GatewayCorsRules.allowOriginValue(origin, properties.getAllowedOrigins());
                if (allowOrigin == null) {
                    writeReject(response, HttpServletResponse.SC_FORBIDDEN, "GATEWAY_CORS_FORBIDDEN",
                            "来源不在允许列表");
                    return;
                }
                response.setHeader(GatewayCorsRules.ALLOW_ORIGIN_HEADER, allowOrigin);
            }
        }

        // 4) 全局限流：仅 API / WebSocket 计入；max<=0 表示关闭
        if (properties.getRateLimitMax() > 0 && GatewayRouteRules.shouldRateLimit(uri)) {
            boolean granted;
            try {
                granted = rateLimiter.tryAcquire(clientIp, properties.getRateLimitMax(),
                        properties.getRateLimitWindowSeconds());
            } catch (RuntimeException e) {
                log.error("网关限流器异常，按配置处置：uri={} clientIp={}", uri, clientIp, e);
                granted = !properties.isFailClosed();
            }
            if (!granted) {
                log.warn("网关限流拒绝：uri={} clientIp={} max={} window={}s", uri, clientIp,
                        properties.getRateLimitMax(), properties.getRateLimitWindowSeconds());
                response.setHeader("Retry-After", String.valueOf(Math.max(1, properties.getRateLimitWindowSeconds())));
                writeReject(response, 429, "GATEWAY_RATE_LIMITED", "请求过于频繁，请稍后重试");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /** 写统一错误响应（与 Result 契约同形：code/message/traceId/timestamp）。 */
    private void writeReject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        String traceId = org.slf4j.MDC.get("traceId");
        String safeTrace = traceId == null ? "-" : traceId.replace("\\", "").replace("\"", "");
        String safeCode = code.replace("\\", "").replace("\"", "");
        String safeMsg = message.replace("\\", "").replace("\"", "");
        String json = "{\"code\":\"" + safeCode + "\",\"message\":\"" + safeMsg
                + "\",\"data\":null,\"traceId\":\"" + safeTrace + "\"}";
        response.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
    }
}
