package com.acme.scaffold.system.file;

import java.time.LocalDateTime;

/**
 * 文件列表 / 详情视图。包含来源功能模块与上传人信息，供「文件管理」页面展示。
 *
 * @param deduplicated 秒传/共享标记（V50）：上传响应中表示本次命中秒传（未落盘）；
 *                     列表行中表示该记录与其它记录共享同一物理文件（stored_name 引用数 > 1）
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
        LocalDateTime createdAt,
        boolean deduplicated) {
}
