package com.acme.scaffold.common.exception;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常映射：将异常转换为统一响应体，敏感细节只进日志不回传客户端。
 *
 * <p>文案统一使用中文常量（i18n 引擎已移除，不再按请求语言解析资源束）。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String MSG_VALIDATION = "请求参数校验失败";
    private static final String MSG_UNAUTHORIZED = "未认证或登录已失效";
    private static final String MSG_FORBIDDEN = "无权限访问该资源";
    private static final String MSG_CONFLICT = "数据冲突，请刷新后重试";
    private static final String MSG_NOT_FOUND = "资源不存在";
    private static final String MSG_METHOD_NOT_ALLOWED = "请求方法不被允许";
    private static final String MSG_UNREADABLE = "请求体格式错误";
    private static final String MSG_MEDIA_TYPE = "不支持的媒体类型，请检查 Content-Type";
    private static final String MSG_NOT_ACCEPTABLE = "无法生成可接受的响应内容，请检查 Accept";
    private static final String MSG_MULTIPART =
            "请以 multipart/form-data 提交文件上传请求，并确认文件字段非空且未超出体积上限";
    private static final String MSG_ARGUMENT_TYPE = "请求参数 {0} 类型不正确，期望 {1}";
    private static final String MSG_INTERNAL = "系统内部错误";
    private static final String MSG_GATEWAY_TIMEOUT = "请求超时，请稍后重试";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = error instanceof FieldError fe ? fe.getField() : error.getObjectName();
            fieldErrors.put(field, error.getDefaultMessage());
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR.code(), MSG_VALIDATION, fieldErrors));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.getErrorCode();
        String message = ex.getMessage();
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
                .body(Result.error(CommonErrorCode.UNAUTHORIZED.code(), MSG_UNAUTHORIZED));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException ex) {
        log.info("鉴权拒绝: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Result.error(CommonErrorCode.FORBIDDEN.code(), MSG_FORBIDDEN));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Result<Void>> handleConflict(DataIntegrityViolationException ex) {
        log.warn("数据完整性冲突: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Result.error(CommonErrorCode.CONFLICT.code(), MSG_CONFLICT));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        String path = request.getRequestURI();
        // D6：特性开关关闭时相关控制器不注册，菜单链接命中 404。
        // 识别禁用特性路径，返回明确的「功能未启用」包络，而非裸 404。
        if (path != null && (path.startsWith("/api/system/chaos")
                || path.startsWith("/api/system/config-center"))) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Result.error(CommonErrorCode.FEATURE_DISABLED.code(),
                            CommonErrorCode.FEATURE_DISABLED.message()));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(CommonErrorCode.NOT_FOUND.code(), MSG_NOT_FOUND));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParameter(MissingServletRequestParameterException ex) {
        // D4：下钻等端点必填参数缺失时，Spring 抛 MissingServletRequestParameterException，
        // 未处理会返回裸 400。统一包络为校验失败，提示缺失参数名。
        log.debug("缺少必填请求参数: {}", ex.getParameterName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR.code(),
                        "缺少必填请求参数: " + ex.getParameterName()));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error("COMMON_405", MSG_METHOD_NOT_ALLOWED));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<Void>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR.code(), MSG_UNREADABLE));
    }

    /**
     * Content-Type 与端点不匹配（如以 text/plain 提交 JSON 请求体）属于调用方用法错误。
     * 此前无映射会落到兜底 handler 变 500，污染服务端错误率；现按 415 返回统一包络。
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("不支持的媒体类型: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(Result.error("COMMON_415", MSG_MEDIA_TYPE));
    }

    /** Accept 与服务端可产出类型不匹配时按 406 返回，避免被误报为 500。 */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Result<Void>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        log.warn("不可接受的响应媒体类型: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(Result.error("COMMON_406", MSG_NOT_ACCEPTABLE));
    }

    /**
     * 文件上传请求不合法（D2）：请求未声明 multipart/form-data、缺少同名 part，或超出上传体积上限。
     *
     * <p>此前无此映射，异常落到兜底 handler 变 500「系统内部错误」——而这类请求是典型的调用方
     * 用法错误，控制器内的 {@code file.isEmpty()} 校验根本没机会执行。统一按 400 返回可自解释的文案。
     * {@code MaxUploadSizeExceededException} 同为本类子类，一并归入 400（提示文件过大）。
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Result<Void>> handleMultipart(MultipartException ex) {
        log.warn("文件上传请求不合法: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR.code(), MSG_MULTIPART));
    }

    /**
     * 请求参数类型不匹配（D3）：如路径变量 {@code /messages/abc/read} 的 {@code abc} 无法转为 Long。
     *
     * <p>此前无此映射，异常落到兜底 handler 变 500，掩盖了「参数用错」这一调用方错误。
     * 现按 400 返回，并在文案中带上参数名与期望类型，便于前端定位。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() == null ? "-" : ex.getRequiredType().getSimpleName();
        log.warn("请求参数类型不匹配: name={} value={} expected={}", ex.getName(), ex.getValue(), expected);
        String message = MSG_ARGUMENT_TYPE.replace("{0}", ex.getName()).replace("{1}", expected);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Result.error(CommonErrorCode.VALIDATION_ERROR.code(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnexpected(Exception ex, HttpServletRequest request) {
        String traceId = org.slf4j.MDC.get("traceId");
        log.error("未预期异常 traceId={} path={}", traceId, request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(CommonErrorCode.INTERNAL_ERROR.code(), MSG_INTERNAL));
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<Result<Void>> handleTimeout(TimeoutException ex) {
        log.warn("请求超时: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                .body(Result.error(CommonErrorCode.GATEWAY_TIMEOUT.code(), MSG_GATEWAY_TIMEOUT));
    }
}
