package com.acme.scaffold.system.tenant.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 更新租户请求（部分更新，仅传入字段参与）。
 *
 * <p>tenant_code 不可变更（外部系统已按此绑定）；状态/联系人/过期/备注可改。</p>
 */
public record UpdateTenantRequest(
        @Size(max = 128, message = "租户名称不超过 128 字符") String tenantName,
        @Size(max = 64, message = "联系人姓名不超过 64 字符") String contactName,
        @Size(max = 32, message = "联系人电话不超过 32 字符") String contactPhone,
        @Size(max = 128, message = "联系人邮箱不超过 128 字符") String contactEmail,
        String status,
        LocalDateTime expireAt,
        @Size(max = 255, message = "备注不超过 255 字符") String remark) {
}
