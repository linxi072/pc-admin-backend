package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.api.dto.PageQuery;

/**
 * 岗位分页查询入参。
 *
 * @param keyword       岗位编码/名称关键词（模糊匹配）
 * @param status        状态筛选：ACTIVE / DISABLED，为空不过滤
 * @param sortField     排序字段（服务端白名单映射，见 PostService）
 * @param sortDirection 排序方向：ASC / DESC，为空按 ASC
 */
public record PostQuery(int page, int size, String keyword, String status,
                        String sortField, String sortDirection) {

    public PageQuery toPageQuery() {
        return PageQuery.of(page, size, sortField, sortDirection);
    }
}
