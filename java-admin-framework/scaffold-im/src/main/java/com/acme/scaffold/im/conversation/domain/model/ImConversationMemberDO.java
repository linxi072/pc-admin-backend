package com.acme.scaffold.im.conversation.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会话成员实体（对应 im_conversation_member，DDL 见 V75__im.sql）。
 *
 * <p>{@code lastReadMessageId} 驱动「未读计数」与「已读回执」：以雪崩 id 单调递增为前提，
 * 某成员未读消息 = 会话内 {@code id > lastReadMessageId 且 sender_id != 该成员} 的消息数；
 * 标记已读即把该字段置为会话内最大消息 id。</p>
 *
 * <p>逻辑删除（deleted），(tenant_id, conversation_id, user_id) 唯一约束兜底并发重复入会。</p>
 */
@Data
public class ImConversationMemberDO {

    private Long id;

    private Long tenantId;

    private Long conversationId;

    private Long userId;

    /** OWNER / ADMIN / MEMBER。 */
    private String memberRole;

    /** 已读游标（消息主键）。 */
    private Long lastReadMessageId;

    private String status;

    private Integer version;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
