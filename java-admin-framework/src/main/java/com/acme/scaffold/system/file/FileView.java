package com.acme.scaffold.system.file;

import java.time.LocalDateTime;

/**
 * 文件列表 / 详情视图。包含来源功能模块与上传人信息，供「文件管理」页面展示。
 */
public record FileView(
        Long id,
        String originalName,
        Long fileSize,
        String contentType,
        String sourceModule,
        String bizId,
        Long uploaderId,
        String uploaderName,
        Long downloadCount,
        LocalDateTime createdAt) {
}
