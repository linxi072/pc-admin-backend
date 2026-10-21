package com.acme.scaffold.security.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级限流注解：标注在任意 Spring Bean 方法（通常为写接口）上，
 * 由 {@link RateLimitAspect} 通过 AOP 拦截并按维度（IP / 用户 / 全局）限流。
 *
 * <p>超限抛出 {@code BusinessException(RATE_LIMITED)}（HTTP 429），复用统一响应体约定。
 * 底层计数复用已有的固定窗口 {@link RateLimiter}（单实例内存计数）；
 * 多实例部署可替换为 Redis 共享计数（保持 key 维度一致）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 窗口内最大请求数，默认 10。 */
    int max() default 10;

    /** 窗口长度（秒），默认 60。 */
    int windowSeconds() default 60;

    /** 限流维度。 */
    Scope scope() default Scope.IP;

    /** 业务自定义 key 后缀，与维度组合以区分同一方法的不同场景。 */
    String key() default "";

    /** 限流维度。 */
    enum Scope {
        /** 按客户端 IP 限流（匿名/登录接口通用）。 */
        IP,
        /** 按当前登录用户限流。 */
        USER,
        /** 全局限流（所有调用方共享一个配额）。 */
        GLOBAL
    }
}
