package com.acme.scaffold.system.tenant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 存量数据归位请求：将全部业务表的历史数据（tenant_id 不等于目标租户）归位到目标租户。
 *
 * <p>典型场景：开启 {@code enforce=true} 前的「历史数据已归位」前置动作，
 * 或单租户存量迁移到指定租户。平台维表（{@code TenantTables#PLATFORM_TABLES}）不在操作范围内。</p>
 */
@Data
public class BackfillTenantRequest {

    /** 目标租户 ID（历史数据归位到该租户）。 */
    @NotNull(message = "目标租户 ID 不能为空")
    private Long targetTenantId;
}
