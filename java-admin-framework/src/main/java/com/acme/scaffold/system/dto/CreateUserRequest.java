package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建用户请求。
 * <p>约束（V6 收敛）：用户仅绑定「单个角色 + 单个部门」，因此角色为必填单值，部门为可选单值。
 */
public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank String displayName,
        String mobile,
        String email,
        Long orgId,
        @NotNull(message = "角色不能为空，用户仅可绑定单个角色") Long roleId) {
}
