package com.acme.scaffold.system.dto;

import com.acme.scaffold.security.permission.DataScopeType;

import java.util.List;

/**
 * 角色数据权限规则视图。
 *
 * @param id           规则 ID；未配置时为 null
 * @param roleId       角色 ID
 * @param resourceCode 资源编码
 * @param scopeType    范围类型
 * @param orgIds       自定义机构 ID 列表；非 CUSTOM 时为空
 * @param orgNames     自定义机构名称，与 {@code orgIds} 同序，用于页面直接展示
 */
public record DataScopeRuleView(
        Long id,
        Long roleId,
        String resourceCode,
        DataScopeType scopeType,
        List<Long> orgIds,
        List<String> orgNames) {
}
