package com.acme.scaffold.system.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 租户开通请求：为已存在的租户初始化管理员 / 角色 / 部门。
 *
 * <p>结构校验由 {@link com.acme.scaffold.system.tenant.TenantProvisioningRules} 兜底；
 * 此处以 Bean Validation 做入参层面的快速失败。adminUsername 可空（缺省 admin）。</p>
 */
public record TenantProvisioningRequest(
        @Size(max = 64, message = "管理员用户名不超过 64 字符") String adminUsername,
        @NotBlank @Size(min = 8, max = 64, message = "管理员初始密码 8–64 位") String adminPassword,
        @NotBlank @Size(max = 64, message = "管理员显示名不超过 64 字符") String adminDisplayName,
        @Size(max = 32, message = "管理员电话不超过 32 字符") String adminMobile,
        @Size(max = 128, message = "管理员邮箱不超过 128 字符") String adminEmail) {
}
