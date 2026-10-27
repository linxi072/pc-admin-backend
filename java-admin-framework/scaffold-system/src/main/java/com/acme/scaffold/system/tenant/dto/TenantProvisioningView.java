package com.acme.scaffold.system.tenant.dto;

/**
 * 租户开通结果视图：返回本次初始化出来的管理员 / 角色 / 部门主键与登录凭据提示。
 */
public record TenantProvisioningView(
        Long tenantId,
        Long adminUserId,
        Long roleId,
        Long deptId,
        String adminUsername,
        String roleCode) {
}
