package com.acme.scaffold.system.dto;

import java.util.List;

/**
 * 更新用户请求（部分更新，null 表示不修改该字段）。
 * <p>约束（多对多）：roleIds / deptIds 传入时整体替换该用户原有的角色 / 部门关联；
 * primaryRoleId / primaryDeptId 可选，标记主角色 / 主部门（为空时取集合首位）。
 */
public record UpdateUserRequest(
        String displayName,
        String mobile,
        String email,
        String status,
        List<Long> roleIds,
        Long primaryRoleId,
        List<Long> deptIds,
        Long primaryDeptId) {
}
