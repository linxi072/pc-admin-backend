package com.acme.scaffold.common.exception;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.error.ErrorCode;
import com.acme.scaffold.common.i18n.I18nService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常映射：将异常转换为统一响应体，敏感细节只进日志不回传客户端。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final I18nService i18n;

    public GlobalExceptionHandler(I18nService i18n) {
        this.i18n = i18n;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            fieldErrors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR.code(),
                        i18n.getMessage("error.validation"), fieldErrors));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        // 默认构造的业务异常经资源束键化（messageKey 非空）按请求语言解析；自定义消息原样透传。
        String message = ex.getMessageKey() != null
                ? i18n.getMessage(ex.getMessageKey())
                : ex.getMessage();
        if (ec.httpStatus() >= 500) {
            log.error("业务异常(服务端): code={} msg={}", ec.code(), ex.getMessage(), ex);
        } else {
            log.warn("业务异常: code={} msg={}", ec.code(), ex.getMessage());
        }
        return ResponseEntity.status(ec.httpStatus())
                .body(Result.error(ec.code(), message));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuth(AuthenticationException ex) {
        log.debug("认证失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(CommonErrorCode.UNAUTHORIZED.code(), i18n.getMessage("error.unauthorized")));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.info("鉴权拒绝: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(CommonErrorCode.FORBIDDEN.code(), i18n.getMessage("error.forbidden")));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleConflict(DataIntegrityViolationException ex) {
        log.warn("数据完整性冲突: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(CommonErrorCode.CONFLICT.code(), i18n.getMessage("error.conflict")));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(CommonErrorCode.NOT_FOUND.code(), i18n.getMessage("error.not_found")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error("COMMON_405", i18n.getMessage("error.method_not_allowed")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR.code(), i18n.getMessage("error.unreadable")));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = org.slf4j.MDC.get("traceId");
        log.error("未预期异常 traceId={} path={}", traceId, request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(CommonErrorCode.INTERNAL_ERROR.code(), i18n.getMessage("error.internal")));
    }
}
