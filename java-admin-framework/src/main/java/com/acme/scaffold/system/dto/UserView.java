package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;

/**
 * 用户视图，绝不外泄 passwordHash / tokenVersion 等敏感字段。
 * <p>约束（V6 收敛）：仅返回「单个角色 + 单个部门」，roleCodes 聚合集合相应收敛为单个角色码。
 *
 * @param orgId     单一部门ID
 * @param orgName   部门名称（展示用）
 * @param roleId    单一角色ID
 * @param roleName  角色名称（展示用）
 * @param roleCode  单一角色编码（鉴权用）
 */
public record UserView(Long id, String username, String displayName, String mobile, String email,
                      Long orgId, String orgName, String status,
                      Long roleId, String roleName, String roleCode,
                      LocalDateTime createdAt) {

    public static UserView from(com.acme.scaffold.system.entity.SysUserDO u,
                                String orgName, String roleName, String roleCode) {
        return new UserView(u.getId(), u.getUsername(), u.getDisplayName(), u.getMobile(), u.getEmail(),
                u.getOrgId(), orgName, u.getStatus(),
                u.getRoleId(), roleName, roleCode, u.getCreatedAt());
    }
}
