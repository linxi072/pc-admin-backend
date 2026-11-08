package com.acme.scaffold.config.multiregion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多区域 / 多集群部署支撑配置（R4），绑定前缀 {@code app.multiregion}。
 *
 * <p><b>核心思想</b>：将「租户」映射到「部署单元（Deployment Unit）」，每个单元对应
 * {@code app.datasources.entries} 中的一个具名数据源（物理库 / 集群）。请求期按当前租户
 * 自动把路由上下文切到对应单元的数据源，实现大租户的物理隔离，复用既有
 * {@code RoutingDataSource} + {@code DataSourceContextHolder} 路由机制（零改 SqlWriters）。</p>
 *
 * <p><b>默认 {@code enabled=false}</b>：不加载任何 R4 Bean、不切换路由上下文，全部回落主库，
 * 与既有单实例 / 单数据源行为零回归。启用后每个单元均为独立物理库，须由 DBA
 * 分别执行 {@code db/init/00_create_database.sql} 与 {@code db/init/01_full_schema.sql} 初始化。</p>
 */
@ConfigurationProperties(prefix = "app.multiregion")
public class MultiRegionProperties {

    /** 是否启用多区域租户路由（默认 false，零回归）。 */
    private boolean enabled = false;

    /**
     * 默认部署单元（数据源名称）。未命中显式映射 / 租户组的租户回落此单元；
     * 留空时取 {@code app.datasources.primary}（主库）。
     */
    private String defaultUnit = "";

    /** 部署单元元数据（名称 → 区域 / 描述）。单元名须与 app.datasources.entries 的 key 一致。 */
    private Map<String, UnitDefinition> units = new LinkedHashMap<>();

    /** 显式租户 → 单元映射（tenantId → 数据源名称），优先级最高。 */
    private Map<Long, String> tenantToUnit = new LinkedHashMap<>();

    /** 租户组 → 单元映射（按租户 ID 区间批量归属，优先级次于显式映射）。 */
    private List<TenantGroupRange> tenantGroups = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }

    public void setDefaultUnit(String defaultUnit) {
        this.defaultUnit = defaultUnit;
    }

    public Map<String, UnitDefinition> getUnits() {
        return units;
    }

    public void setUnits(Map<String, UnitDefinition> units) {
        this.units = units;
    }

    public Map<Long, String> getTenantToUnit() {
        return tenantToUnit;
    }

    public void setTenantToUnit(Map<Long, String> tenantToUnit) {
        this.tenantToUnit = tenantToUnit;
    }

    public List<TenantGroupRange> getTenantGroups() {
        return tenantGroups;
    }

    public void setTenantGroups(List<TenantGroupRange> tenantGroups) {
        this.tenantGroups = tenantGroups;
    }
}
