package com.acme.scaffold.system.dto;

/**
 * 更新用户请求（部分更新，null 表示不修改该字段）。
 * <p>约束（V6 收敛）：roleId / orgId 均为单值，null 表示保持原绑定不变。
 */
public record UpdateUserRequest(
        String displayName,
        String mobile,
        String email,
        Long orgId,
        String status,
        Long roleId) {
}
