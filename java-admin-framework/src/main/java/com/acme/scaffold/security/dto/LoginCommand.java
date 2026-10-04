package com.acme.scaffold.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参。注意：不直接接收实体，且禁止在日志/审计中记录 password。
 */
public record LoginCommand(@NotBlank String username, @NotBlank String password) {
}
