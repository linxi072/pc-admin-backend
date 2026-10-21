package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信收件明细：记录「某人收到某条消息」及其已读状态与回执时间。
 * 唯一键 (tenant_id, message_id, user_id) 保证批量发送不产生重复投递。
 */
@Data
public class SysMessageReceiptDO {

    private Long id;

    private Long tenantId = 0L;
    private Long messageId;
    private Long userId;
    /** 0=未读 1=已读 */
    private Integer isRead = 0;
    /** 已读回执时间 */
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
