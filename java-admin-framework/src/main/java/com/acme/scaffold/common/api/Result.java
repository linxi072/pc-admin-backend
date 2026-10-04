package com.acme.scaffold.common.api;

import java.time.Instant;

/**
 * 统一响应体。约定：
 * <ul>
 *   <li>HTTP 状态表达协议结果（400/401/403/404/409/422/429/500），不要为了统一而全部返回 200；</li>
 *   <li>code 表达稳定业务错误（如 AUTH_001、SYSTEM_USER_003）；</li>
 *   <li>下载、流式、Actuator、OpenAPI 响应不包装。</li>
 * </ul>
 */
public record Result<T>(String code, String message, T data, String traceId, Instant timestamp) {

    public static <T> Result<T> success(T data) {
        return new Result<>("0", "OK", data, currentTraceId(), Instant.now());
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> error(String code, String message) {
        return new Result<>(code, message, null, currentTraceId(), Instant.now());
    }

    public static <T> Result<T> error(String code, String message, T data) {
        return new Result<>(code, message, data, currentTraceId(), Instant.now());
    }

    private static String currentTraceId() {
        String tid = org.slf4j.MDC.get("traceId");
        return tid == null ? "-" : tid;
    }

    public boolean isSuccess() {
        return "0".equals(code);
    }
}
