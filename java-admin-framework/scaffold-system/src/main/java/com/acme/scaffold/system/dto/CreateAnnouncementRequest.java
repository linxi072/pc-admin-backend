package com.acme.scaffold.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建公告请求。初始状态一律为 DRAFT（草稿），需显式调用发布接口。
 */
public record CreateAnnouncementRequest(
        @NotBlank @Size(max = 200, message = "{validation.title.size}") String title,
        @NotBlank(message = "{validation.announcement.content.notBlank}") String content,
        Integer isTop,
        LocalDateTime publishAt,
        LocalDateTime expireAt) {
}
