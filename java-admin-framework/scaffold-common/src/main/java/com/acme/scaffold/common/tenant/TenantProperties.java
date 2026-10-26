package com.acme.scaffold.common.tenant;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * 是否强制注入 tenant_id（行级隔离实际生效开关）。默认 false：
     * 需显式置 true 才会由 {@code JooqWriters}（经 {@code TenantEnforcement} 单点裁决）对读/改/删追加 tenant_id 过滤，
     * 避免对既有单租户数据与单元测试产生回归。开启前应确保业务表 tenant_id 已正确落库。
     */
    private boolean enforce = false;

    /**
     * 平台共享维表（不受租户隔离约束，跨租户全局可见），表名小写。
     * 这些表由平台统一维护（租户维度、菜单、字典、参数、权限资源等），隔离生效时也不追加 tenant_id 过滤。
     */
    private List<String> platformTables = new ArrayList<>(List.of(
            "sys_tenant", "sys_menu", "sys_dict_type", "sys_dict_data", "sys_config", "sys_api_resource"));
}
