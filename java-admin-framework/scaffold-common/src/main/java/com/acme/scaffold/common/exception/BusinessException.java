package com.acme.scaffold.common.exception;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.error.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：携带稳定错误码与可公开信息，异常消息不应包含 SQL、堆栈或敏感细节。
 *
 * <p>文案统一使用中文（i18n 引擎已移除）：由错误码默认消息构造时使用错误码自带的中文文案，
 * 由显式消息构造时原样透传。两者都通过 {@link #getMessage()} 供日志与响应使用。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object data;

    /** 由错误码默认消息构造。 */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    /** 由显式消息构造（中文文案透传）。 */
    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
    }

    public static BusinessException of(ErrorCode errorCode, String message) {
        return new BusinessException(errorCode, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(CommonErrorCode.CONFLICT, message);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(CommonErrorCode.VALIDATION_ERROR, message);
    }
}
