package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.api.PageQuery;

/**
 * 用户分页查询入参。
 * <p>约束（V6 收敛）：orgId / roleId 均为单值筛选条件（用户仅归属单部门、单角色）。
 */
public record UserQuery(int page, int size, String username, String status, Long orgId, Long roleId) {

    public PageQuery toPageQuery() {
        return PageQuery.of(page, size);
    }
}
