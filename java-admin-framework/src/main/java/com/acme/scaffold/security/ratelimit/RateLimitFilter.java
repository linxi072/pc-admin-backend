package com.acme.scaffold.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 认证接口限流过滤器：针对 {@code /api/auth/login} 与 {@code /api/auth/refresh} 按客户端 IP 限流，
 * 防暴力破解与重放。超出阈值返回 429（JSON 体）。其余接口不限制。
 *
 * <p>阈值当前为常量（{@link #MAX_PER_WINDOW} / {@link #WINDOW_SECONDS}），后续可外置到配置。
 * 单实例内存计数；多实例部署建议替换为 Redis 共享计数。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_PER_WINDOW = 10;
    private static final int WINDOW_SECONDS = 60;

    private final RateLimiter limiter = new RateLimiter();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        boolean authEndpoint = ("/api/auth/login".equals(uri) && "POST".equals(method))
                || ("/api/auth/refresh".equals(uri) && "POST".equals(method));
        if (authEndpoint) {
            String ip = clientIp(request);
            if (!limiter.tryAcquire("login:" + ip, MAX_PER_WINDOW, WINDOW_SECONDS)) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"AUTH_010\",\"message\":\"请求过于频繁，请稍后再试\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    /** 取客户端真实 IP：优先 X-Forwarded-For 首段，回退到远端地址。 */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
