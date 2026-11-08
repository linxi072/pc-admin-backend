package com.acme.scaffold.ai;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 模块异常建议（F5）：以最高优先级拦截 {@link LlmNotImplementedException}，
 * 返回 HTTP 501 Not Implemented 与可读错误体，确保不被通用全局异常处理器（catch-all Exception → 500）改写。
 *
 * <p>仅覆盖本模块特有的「能力未就绪」语义；其余异常仍交由 scaffold-common 的全局处理器统一编排。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.acme.scaffold.ai")
public class AiExceptionAdvice {

    @ExceptionHandler(LlmNotImplementedException.class)
    public ResponseEntity<Map<String, Object>> handleLlmNotImplemented(LlmNotImplementedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "LLM_NOT_IMPLEMENTED");
        body.put("message", ex.getMessage());
        body.put("status", HttpStatus.NOT_IMPLEMENTED.value());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(body);
    }
}
