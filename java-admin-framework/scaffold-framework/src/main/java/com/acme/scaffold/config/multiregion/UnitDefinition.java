package com.acme.scaffold.config.multiregion;

/**
 * 部署单元（物理库 / 集群）元数据。
 *
 * <p>单元名（map key）须与 {@code app.datasources.entries} 的具名数据源 key 一致；
 * 本记录仅承载观测 / 文档用途的区域与描述信息，不参与路由决策（路由决策由
 * {@link TenantUnitMapping} 基于租户映射完成）。
 */
public record UnitDefinition(String region, String description) {

    public UnitDefinition {
        if (region == null) {
            region = "";
        }
        if (description == null) {
            description = "";
        }
    }
}
