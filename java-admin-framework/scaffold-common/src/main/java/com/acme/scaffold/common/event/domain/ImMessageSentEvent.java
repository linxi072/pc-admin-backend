package com.acme.scaffold.common.event.domain;

import com.acme.scaffold.common.event.DomainEvent;

import java.util.List;

/**
 * 站内消息已发送事件（ROADMAP R6 · 解耦示范）。
 *
 * <p>由 {@code scaffold-im} 模块在「消息成功落库」后发布。订阅方（如 {@code realtime}
 * 经 WebSocket 向接收方实时推送）各自响应，{@code im} 不再直连推送通道，实现模块解耦。</p>
 *
 * <p>事件仅携带轻量摘要（接收方列表 + 内容预览），避免把完整消息体广播给所有订阅者；
 * 接收端如需正文，经 REST 拉取即可。</p>
 */
public class ImMessageSentEvent extends DomainEvent {

    /** 会话主键。 */
    private final Long conversationId;

    /** 消息主键。 */
    private final Long messageId;

    /** 发送者主键。 */
    private final Long senderId;

    /** 接收方主键集合（已排除发送者自身，避免自推送）。 */
    private final List<Long> receiverIds;

    /** 会话类型（P2P / GROUP），便于前端渲染差异化。 */
    private final String conversationType;

    /** 内容预览（截断后的纯文本），用于实时推送气泡与通知摘要。 */
    private final String contentPreview;

    public ImMessageSentEvent(Long tenantId, String source, Long conversationId, Long messageId,
                              Long senderId, List<Long> receiverIds, String conversationType,
                              String contentPreview) {
        super(tenantId, source);
        this.conversationId = conversationId;
        this.messageId = messageId;
        this.senderId = senderId;
        this.receiverIds = receiverIds;
        this.conversationType = conversationType;
        this.contentPreview = contentPreview;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public Long getMessageId() {
        return messageId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public List<Long> getReceiverIds() {
        return receiverIds;
    }

    public String getConversationType() {
        return conversationType;
    }

    public String getContentPreview() {
        return contentPreview;
    }
}
