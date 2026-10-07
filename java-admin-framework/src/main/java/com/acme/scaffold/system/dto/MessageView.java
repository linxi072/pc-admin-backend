package com.acme.scaffold.system.dto;

import java.time.LocalDateTime;

/**
 * 站内信视图（收件人视角）。
 *
 * @param messageId 消息ID
 * @param title     标题
 * @param content   内容
 * @param msgType   类型
 * @param senderId  发送人
 * @param senderName发送人名称
 * @param sentAt    发送时间
 * @param isRead    是否已读
 * @param readAt    已读回执时间（未读为 null）
 */
public record MessageView(Long messageId, String title, String content, String msgType,
                          Long senderId, String senderName, LocalDateTime sentAt,
                          boolean isRead, LocalDateTime readAt) {
}
