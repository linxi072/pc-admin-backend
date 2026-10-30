package com.acme.scaffold.system.dto;

import com.acme.scaffold.common.api.dto.PageQuery;

/**
 * 站内信回执分页查询入参。
 *
 * @param messageId  站内信 ID（路径参数绑定，必填，且必须是当前用户发出）
 * @param keyword    接收人关键字（匹配 display_name / username，模糊匹配）；为空忽略
 * @param readStatus 回执状态筛选：{@code "READ"} 仅已读、{@code "UNREAD"} 仅未读、{@code null} 全部
 */
public record MessageReceiptQuery(Long messageId, int page, int size, String keyword, String readStatus) {

    public PageQuery toPageQuery() {
        return PageQuery.of(page, size, null, null);
    }

    /** 是否按关键字过滤接收人（用户被删时 display_name 缺失，仍可按 username 命中）。 */
    public boolean hasKeyword() {
        return keyword != null && !keyword.isBlank();
    }

    /** 是否按回执状态过滤。 */
    public boolean hasReadStatus() {
        return readStatus != null && !readStatus.isBlank();
    }
}
