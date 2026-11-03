package com.acme.scaffold.config.multiregion;

import java.util.List;
import java.util.Map;

/**
 * 租户 → 部署单元（数据源名）解析器（R4 核心纯逻辑，可独立单测）。
 *
 * <p>解析优先级：
 * <ol>
 *   <li>显式映射 {@code tenantToUnit}（tenantId 精确命中）；</li>
 *   <li>租户组区间 {@code tenantGroups}（首个命中区间）；</li>
 *   <li>默认单元 {@code defaultUnit}（缺省回落 primary）。</li>
 * </ol>
 *
 * <p>本类不依赖 Spring / 数据源，便于在单元测试中以静态工厂 {@link #from} 直接构建。</p>
 */
public class TenantUnitMapping {

    private final String defaultUnitName;
    private final Map<Long, String> explicitTenants;
    private final List<TenantGroupRange> tenantGroups;

    private TenantUnitMapping(String defaultUnitName,
                              Map<Long, String> explicitTenants,
                              List<TenantGroupRange> tenantGroups) {
        this.defaultUnitName = defaultUnitName;
        this.explicitTenants = explicitTenants;
        this.tenantGroups = tenantGroups;
    }

    /**
     * 由配置构建映射。
     *
     * @param props                   多区域配置（enabled 与否均可，此处仅取映射项）
     * @param primaryDataSourceName   主数据源名（defaultUnit 缺省时回落）
     */
    public static TenantUnitMapping from(MultiRegionProperties props, String primaryDataSourceName) {
        String def = (props.getDefaultUnit() == null || props.getDefaultUnit().isBlank())
                ? primaryDataSourceName
                : props.getDefaultUnit();
        return create(def, props.getTenantToUnit(), props.getTenantGroups());
    }

    /**
     * 直接以参数构建映射（供测试与编程式构造）。
     *
     * @param defaultUnitName   默认单元（数据源名）
     * @param explicitTenants   显式租户 → 单元映射（可空）
     * @param tenantGroups      租户组区间 → 单元映射（可空）
     */
    public static TenantUnitMapping create(String defaultUnitName,
                                           Map<Long, String> explicitTenants,
                                           List<TenantGroupRange> tenantGroups) {
        return new TenantUnitMapping(
                defaultUnitName,
                explicitTenants != null ? explicitTenants : Map.of(),
                tenantGroups != null ? tenantGroups : List.of());
    }

    /** 解析租户所属部署单元（数据源名称）。 */
    public String resolve(long tenantId) {
        String explicit = explicitTenants.get(tenantId);
        if (explicit != null) {
            return explicit;
        }
        for (TenantGroupRange range : tenantGroups) {
            if (range != null && range.contains(tenantId)) {
                return range.getUnit();
            }
        }
        return defaultUnitName;
    }

    /** 默认单元名称（未命中任何映射时的回落目标）。 */
    public String defaultUnitName() {
        return defaultUnitName;
    }
}
