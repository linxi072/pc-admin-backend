package com.acme.scaffold.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参。注意：不直接接收实体，且禁止在日志/审计中记录 password。
 * captchaToken / captchaAnswer 为可选字段：仅当 {@code auth.captcha.enabled=true} 时由前端回传。
 */
public record LoginCommand(
        @NotBlank String username,
        @NotBlank String password,
        String captchaToken,
        String captchaAnswer) {
}
