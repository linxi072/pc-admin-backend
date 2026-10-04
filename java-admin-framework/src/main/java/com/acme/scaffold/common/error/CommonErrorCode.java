package com.acme.scaffold.common.error;

import org.springframework.http.HttpStatus;

/**
 * 框架级通用错误码。
 */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_ERROR("COMMON_001", "请求参数校验失败", HttpStatus.BAD_REQUEST.value()),
    UNAUTHORIZED("COMMON_401", "未认证或登录已失效", HttpStatus.UNAUTHORIZED.value()),
    FORBIDDEN("COMMON_403", "无权限访问该资源", HttpStatus.FORBIDDEN.value()),
    NOT_FOUND("COMMON_404", "资源不存在", HttpStatus.NOT_FOUND.value()),
    CONFLICT("COMMON_409", "数据冲突，请刷新后重试", HttpStatus.CONFLICT.value()),
    BUSINESS_ERROR("COMMON_422", "业务处理失败", HttpStatus.UNPROCESSABLE_ENTITY.value()),
    RATE_LIMITED("COMMON_429", "请求过于频繁，请稍后再试", HttpStatus.TOO_MANY_REQUESTS.value()),
    INTERNAL_ERROR("COMMON_500", "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR.value());

    private final String code;
    private final String message;
    private final int httpStatus;

    CommonErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
