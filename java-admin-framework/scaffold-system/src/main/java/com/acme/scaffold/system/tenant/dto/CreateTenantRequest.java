package com.acme.scaffold.system.tenant.dto;

import com.acme.scaffold.common.tenant.TenantRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建租户请求。
 *
 * <p>结构校验由 {@link TenantRules#validateTenant} 兜底；此处以 Bean Validation 做入参层面的快速失败
 * （非空与长度上限）。tenant_code 创建后不可变更（外部系统已按此绑定）。</p>
 */
public record CreateTenantRequest(
        @NotBlank @Size(max = 64, message = "租户编码不超过 64 字符") String tenantCode,
        @NotBlank @Size(max = 128, message = "租户名称不超过 128 字符") String tenantName,
        @Size(max = 64, message = "联系人姓名不超过 64 字符") String contactName,
        @Size(max = 32, message = "联系人电话不超过 32 字符") String contactPhone,
        @Size(max = 128, message = "联系人邮箱不超过 128 字符") String contactEmail,
        String status,
        LocalDateTime expireAt,
        @Size(max = 255, message = "备注不超过 255 字符") String remark) {
}
