package com.acme.scaffold.security.error;

import com.acme.scaffold.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SecurityErrorCode implements ErrorCode {

    BAD_CREDENTIALS("AUTH_001", "用户名或密码错误", HttpStatus.UNAUTHORIZED.value()),
    ACCOUNT_LOCKED("AUTH_002", "账号已锁定，请稍后再试", 423),
    ACCOUNT_DISABLED("AUTH_003", "账号已禁用", HttpStatus.FORBIDDEN.value()),
    REFRESH_INVALID("AUTH_004", "刷新令牌无效或已过期", HttpStatus.UNAUTHORIZED.value()),
    TOKEN_REVOKED("AUTH_005", "凭证已失效，请重新登录", HttpStatus.UNAUTHORIZED.value()),
    PASSWORD_WEAK("AUTH_006", "密码强度不足", HttpStatus.UNPROCESSABLE_ENTITY.value()),
    PASSWORD_REUSED("AUTH_007", "不能与最近使用的密码相同", HttpStatus.UNPROCESSABLE_ENTITY.value()),
    CAPTCHA_REQUIRED("AUTH_008", "请先获取并完成验证码", HttpStatus.BAD_REQUEST.value()),
    CAPTCHA_INVALID("AUTH_009", "验证码错误或已失效", HttpStatus.BAD_REQUEST.value()),
    RATE_LIMITED("AUTH_010", "请求过于频繁，请稍后再试", 429);

    private final String code;
    private final String message;
    private final int httpStatus;

    SecurityErrorCode(String code, String message, int httpStatus) {
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
