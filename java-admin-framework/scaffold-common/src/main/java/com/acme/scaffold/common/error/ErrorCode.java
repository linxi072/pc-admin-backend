package com.acme.scaffold.common.error;

/**
 * 错误码契约：稳定的业务错误码 + 对应的 HTTP 状态。模块错误码按 {@code 模块前缀 + 三位编号} 命名，
 * 例如 {@code AUTH_001}、{@code SYSTEM_USER_003}，HTTP 状态另行映射。
 */
public interface ErrorCode {

    String code();

    String message();

    int httpStatus();
}
