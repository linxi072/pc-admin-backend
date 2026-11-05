package com.acme.scaffold.common.resilience;

/**
 * 熔断器处于 {@code OPEN} 状态、请求被快速失败时抛出，供调用方感知并降级（如走兜底/死信）。
 */
public class CircuitBreakerOpenException extends RuntimeException {

    public CircuitBreakerOpenException(String message) {
        super(message);
    }
}
