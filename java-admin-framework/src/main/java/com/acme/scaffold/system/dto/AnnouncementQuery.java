package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.api.PageQuery;

/**
 * 公告分页查询入参。
 *
 * @param keyword   标题/内容关键词（模糊匹配）
 * @param status    状态筛选：DRAFT / PUBLISHED / OFFLINE，为空不过滤
 * @param onlyValid true 时仅返回当前有效（已发布且在有效期内）的公告
 */
public record AnnouncementQuery(int page, int size, String keyword, String status, Boolean onlyValid) {

    public PageQuery toPageQuery() {
        return PageQuery.of(page, size);
    }
}
