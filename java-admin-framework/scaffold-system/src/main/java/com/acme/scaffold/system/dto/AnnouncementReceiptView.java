package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;

/**
 * 公告已读回执视图：谁在何时已读。
 *
 * @param displayName 阅读人显示名（用户可能已被删除，此时为 null，前端降级展示 userId）
 */
public record AnnouncementReceiptView(Long id, Long announcementId, Long userId,
                                      String displayName, LocalDateTime readAt) {
}
