package com.acme.scaffold.security.antireplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 请求级防重放过滤器：针对 {@code /api/auth/login} 与 {@code /api/auth/refresh}，
 * 校验客户端时间戳（{@code X-Request-Timestamp}）在允许时钟偏差内，并确保一次性
 * nonce（{@code X-Request-Nonce}）未被使用过，防止重放攻击。
 *
 * <p>仅当 {@link AntiReplayProperties#isEnabled()} 为 true 时生效（默认关闭，opt-in），
 * 以避免在没有前端配合时阻断现有登录链路。未开启时 {@link #shouldNotFilter} 直接放行。
 *
 * <p>执行顺序贴近 {@link com.acme.scaffold.security.ratelimit.RateLimitFilter}（HIGHEST_PRECEDENCE+5），
 * 这里取 +4，先于限流执行，使非法/重放请求尽早被拒。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class AntiReplayFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AntiReplayFilter.class);

    private static final List<RequestMatcher> TARGETS = List.of(
            new AntPathRequestMatcher("/api/auth/login", "POST"),
            new AntPathRequestMatcher("/api/auth/refresh", "POST"));

    private static final String H_TS = "X-Request-Timestamp";
    private static final String H_NONCE = "X-Request-Nonce";

    private final AntiReplayProperties props;
    private final ReplayNonceService nonceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public AntiReplayFilter(AntiReplayProperties props, ReplayNonceService nonceService) {
        this.props = props;
        this.nonceService = nonceService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!props.isEnabled()) {
            return true;
        }
        return TARGETS.stream().noneMatch(m -> m.matches(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String tsHeader = request.getHeader(H_TS);
        String nonce = request.getHeader(H_NONCE);

        Long clientTs = parseLong(tsHeader);
        ReplayProtectionRules.TimestampVerdict verdict =
                ReplayProtectionRules.verifyTimestamp(clientTs, System.currentTimeMillis(), props.getMaxClockSkewMillis());
        if (verdict != ReplayProtectionRules.TimestampVerdict.OK) {
            if (log.isDebugEnabled()) {
                log.debug("anti-replay timestamp rejected: verdict={}, header={}", verdict, tsHeader);
            }
            writeError(response, HttpStatus.BAD_REQUEST, "AUTH_012",
                    "请求时间戳无效或超出允许偏差（anti-replay）: " + verdict);
            return;
        }

        if (nonce == null || nonce.isBlank()) {
            writeError(response, HttpStatus.BAD_REQUEST, "AUTH_012", "缺少防重放随机数 nonce");
            return;
        }

        if (nonceService.isReplay(nonce)) {
            writeError(response, HttpStatus.CONFLICT, "AUTH_013", "检测到重复请求（nonce 已被使用，疑似重放）");
            return;
        }

        chain.doFilter(request, response);
    }

    private Long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private void writeError(HttpServletResponse response, HttpStatus status, String code, String msg) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(
                Map.of("code", code, "message", msg, "success", false)));
    }
}
