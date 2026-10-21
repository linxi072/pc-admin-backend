package com.acme.scaffold.common.error;

/**
 * 错误码契约：稳定的业务错误码 + 对应的 HTTP 状态。模块错误码按 {@code 模块前缀 + 三位编号} 命名，
 * 例如 {@code AUTH_001}、{@code SYSTEM_USER_003}，HTTP 状态另行映射。
 */
public interface ErrorCode {

    String code();

    String message();

    int httpStatus();

    /**
     * 资源束消息键。约定为 {@code error.<code()>}，由各实现枚举自动继承，无需逐条声明。
     *
     * <p>{@code GlobalExceptionHandler} 在响应中经 {@code I18nService} 按当前请求语言解析该键，
     * 实现业务异常消息的本地化；资源束位于 {@code i18n/messages*.properties}，
     * 缺失键经 {@code I18nService} 回退为键本身（见 {@code ErrorCode} 契约与 {@code I18nService}）。
     */
    default String messageKey() {
        return "error." + code();
    }
}
