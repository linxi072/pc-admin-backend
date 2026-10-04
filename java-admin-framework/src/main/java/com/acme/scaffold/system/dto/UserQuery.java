package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.api.PageQuery;

/**
 * 用户分页查询入参。
 */
public record UserQuery(int page, int size, String username, String status, Long orgId) {

    public PageQuery toPageQuery() {
        return PageQuery.of(page, size);
    }
}
