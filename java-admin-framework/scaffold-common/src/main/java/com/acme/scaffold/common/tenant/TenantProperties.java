package com.acme.scaffold.common.tenant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 多租户隔离配置（M19）。
 *
 * <p>绑定自 {@code app.tenant.*}，默认启用隔离（enabled=true）、默认租户为平台租户(0)。
 * 解析优先级由 {@link TenantResolver} 实现：请求头 > Token Claim > 默认租户。</p>
 *
 * <p><b>强制注入与越权拦截的实际生效在 P3</b>（依赖业务表 {@code tenant_id} 列），本配置仅描述策略。
 * {@code enabled=false} 时 {@link TenantResolver} 强制回落默认租户，可用于灰度关闭隔离。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.tenant")
public class TenantProperties {

    /** 是否启用多租户隔离（强制 tenant_id 注入与越权拦截）。默认 true。 */
    private boolean enabled = true;

    /** 默认租户 ID（未解析到租户时回落值）。默认 0（平台租户）。 */
    private Long defaultTenantId = TenantRules.PLATFORM_TENANT_ID;

    /** 平台管理员角色码（命中即视为平台管理员作用域，可跨租户）。默认 PLATFORM_ADMIN。 */
    private String platformAdminRoleCode = "PLATFORM_ADMIN";

    /** 租户请求头名称（显式覆盖当前租户）。默认 X-Tenant-Id。 */
    private String headerName = "X-Tenant-Id";

    /** JWT/Token 中承载租户 ID 的声明名。默认 tid。 */
    private String claimName = "tid";
}
