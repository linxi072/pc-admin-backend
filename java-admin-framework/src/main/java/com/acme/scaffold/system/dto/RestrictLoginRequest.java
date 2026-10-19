package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 设置临时限制登录入参。
 *
 * <p>durationMinutes 取值 {@link com.acme.scaffold.system.online.rules.RestrictionRules#ALLOWED_DURATIONS}
 * 白名单（30 分钟 ~ 7 天，0 为长期）；reason 必填，用于审计与限制记录展示。
 */
public record RestrictLoginRequest(
        @NotNull(message = "{validation.restrictLogin.duration.notNull}")
        Integer durationMinutes,
        @NotBlank(message = "{validation.restrictLogin.reason.notBlank}")
        @Size(max = 255, message = "{validation.restrictLogin.reason.size}")
        String reason) {
}
