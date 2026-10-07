package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 编辑公告请求（全量覆盖语义，null 表示清空该字段）。
 * 仅 DRAFT / OFFLINE 状态允许编辑；已发布公告需先下线再编辑，避免线上内容被静默篡改。
 */
public record UpdateAnnouncementRequest(
        @Size(max = 200, message = "标题长度不能超过200") String title,
        String content,
        Integer isTop,
        LocalDateTime publishAt,
        LocalDateTime expireAt) {
}
