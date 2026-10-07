package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.api.PageQuery;

/**
 * 用户分页查询入参。
 * <p>约束（V6 收敛）：orgId / roleId 均为单值筛选条件（用户仅归属单部门、单角色）。
 */
public record UserQuery(int page, int size, String username, String status, Long orgId, Long roleId) {

    /**
     * 用户资源的编码，与 {@code sys_role_data_scope.resource_code} 对应。
     * 数据权限配置页面按该编码读写规则，故提取为常量避免散落字面量。
     */
    public static final String RESOURCE_CODE = "system:user";

    public PageQuery toPageQuery() {
        return PageQuery.of(page, size);
    }
}
