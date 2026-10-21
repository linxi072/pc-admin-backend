package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建用户请求。
 * <p>约束（多对多）：用户可绑定多个角色、归属多个部门。
 * roleIds / deptIds 为必填集合（至少 1 个）；primaryRoleId / primaryDeptId 可选，
 * 用于标记主角色 / 主部门（为空时取对应集合的首位），决定权限聚合与数据范围的「主」维度。
 * <p>postIds 为可选集合（岗位是兼任属性，不构成必填约束）；传入时逐个校验存在性。
 */
public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Size(min = 8, max = 64) String password,
        @NotBlank String displayName,
        String mobile,
        String email,
        @NotEmpty(message = "{validation.user.roleIds.notEmpty}") List<Long> roleIds,
        Long primaryRoleId,
        @NotEmpty(message = "{validation.user.deptIds.notEmpty}") List<Long> deptIds,
        Long primaryDeptId,
        List<Long> postIds) {
}
