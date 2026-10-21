package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;

/**
 * 站内信回执视图：谁（未）已读、何时已读。
 *
 * <p>与公告回执（仅含已读行）不同，站内信是 eager 模型，回执表同时含已读与未读行，
 * 故视图需要 {@code isRead} 区分两端；未读行的 {@code readAt} 为 null。
 *
 * @param displayName 阅读人显示名（用户可能已被删除，此时为 null，前端降级展示 userId）
 * @param isRead      是否已读（回执表 is_read：true=已读，false=未读）
 */
public record MessageReceiptView(Long id, Long messageId, Long userId,
                                 String displayName, Boolean isRead,
                                 LocalDateTime readAt) {
}
