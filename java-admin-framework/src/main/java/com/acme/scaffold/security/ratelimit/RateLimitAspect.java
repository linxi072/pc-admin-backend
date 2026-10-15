package com.acme.scaffold.security.ratelimit;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 方法级限流切面：拦截带 {@link RateLimit} 注解的方法，按注解声明的维度（IP / 用户 / 全局）
 * 与阈值进行固定窗口限流。复用已有的 {@link RateLimiter} 计数实现。
 *
 * <p>超限抛出 {@link BusinessException#of(CommonErrorCode, String)}（RATE_LIMITED → HTTP 429），
 * 由全局异常处理器统一包装为 {@code Result} 响应体（与项目「HTTP 状态码表达协议结果」约定一致）。
 *
 * <p>与 {@link RateLimitFilter}（URL 级登录限流）互补：前者按路由粗粒度拦截认证接口，
 * 本切面按注解细粒度保护任意写接口，无需在过滤器里硬编码路径。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiter rateLimiter;
    private final SecurityContextFacade securityContextFacade;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String key = buildKey(pjp, rateLimit);
        if (!rateLimiter.tryAcquire(key, rateLimit.max(), rateLimit.windowSeconds())) {
            log.warn("方法级限流触发: key={} max={}/{}s", key, rateLimit.max(), rateLimit.windowSeconds());
            throw BusinessException.of(CommonErrorCode.RATE_LIMITED, "请求过于频繁，请稍后再试");
        }
        return pjp.proceed();
    }

    private String buildKey(ProceedingJoinPoint pjp, RateLimit rateLimit) {
        String methodId = pjp.getTarget().getClass().getSimpleName() + "#" + pjp.getSignature().getName();
        String suffix = rateLimit.key().isBlank() ? "" : (":" + rateLimit.key());
        return switch (rateLimit.scope()) {
            case IP -> "ip:" + clientIp() + ":" + methodId + suffix;
            case USER -> "user:" + userId() + ":" + methodId + suffix;
            case GLOBAL -> "global:" + methodId + suffix;
        };
    }

    private String clientIp() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                return xff.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        }
        return "unknown";
    }

    private String userId() {
        try {
            CurrentPrincipal principal = securityContextFacade.requireCurrentPrincipal();
            return String.valueOf(principal.userId());
        } catch (RuntimeException e) {
            // 未登录（或上下文不可解析）时回退到 IP，避免空 key 导致跨用户共享计数
            return clientIp();
        }
    }
}
