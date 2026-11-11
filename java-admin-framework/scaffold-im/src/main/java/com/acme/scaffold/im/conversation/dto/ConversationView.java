package com.acme.scaffold.im.conversation.dto;

import java.io.Serializable;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/**
 * 会话视图。聚合成员列表、未读计数与最近一条消息预览，供会话列表/详情直接渲染。
 */
@Data
public class ConversationView implements Serializable {

    private Long id;

    private String conversationType;

    private String title;

    private Long ownerId;

    private List<Long> memberIds;

    private Integer memberCount;

    private Long unreadCount;

    private String lastMessagePreview;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
