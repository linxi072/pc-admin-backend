package com.acme.scaffold.common.exception;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.error.ErrorCode;
import lombok.Getter;

/**
 * 业务异常：携带稳定错误码与可公开信息，异常消息不应包含 SQL、堆栈或敏感细节。
 *
 * <p>消息本地化（Phase D）：
 * <ul>
 *   <li>由错误码<b>默认消息</b>构造时（{@code new BusinessException(errorCode)}），
 *       {@link #getMessageKey()} 非空（值为 {@code error.<CODE>}，由 {@link ErrorCode#messageKey()} 派生），
 *       {@code GlobalExceptionHandler} 据此经 {@code I18nService} 按请求语言解析出本地化文案；</li>
 *   <li>由<b>显式消息</b>构造时（{@code of(errorCode, "...")} / {@code conflict(...)} / {@code badRequest(...)}），
 *       {@link #getMessageKey()} 为 {@code null}，响应原样回传该文案（向后兼容既有中文透传行为）；</li>
 *   <li>{@link #getMessage()} 始终返回中文兜底文案（错误码默认消息或显式消息），便于日志阅读，不受请求语言影响。</li>
 * </ul>
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object data;
    /** 资源束键；仅当由错误码默认消息构造时非空，供 handler 做本地化解析。 */
    private final String messageKey;

    /** 由错误码默认消息构造：响应消息走资源束键化（messageKey = error.<CODE>）。 */
    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.message(), null);
    }

    /** 由显式消息构造：向后兼容，中文文案原样透传，不键化。 */
    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    public BusinessException(ErrorCode errorCode, String message, Object data) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
        // 仅当消息恰为错误码默认中文文案时视为键化路径（自定义消息保持透传）。
        this.messageKey = message != null && message.equals(errorCode.message())
                ? errorCode.messageKey() : null;
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
