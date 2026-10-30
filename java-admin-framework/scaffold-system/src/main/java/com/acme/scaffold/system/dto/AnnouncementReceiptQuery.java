package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.api.dto.PageQuery;

/**
 * 公告已读回执分页查询入参。
 *
 * @param announcementId 公告 ID（路径参数绑定，必填）
 * @param keyword        阅读人关键字（匹配 display_name / username，模糊匹配）
 */
public record AnnouncementReceiptQuery(Long announcementId, int page, int size, String keyword) {

    public PageQuery toPageQuery() {
        return PageQuery.of(page, size, null, null);
    }
}
