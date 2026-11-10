package com.acme.scaffold.im.message.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息视图。撤回后 content 为 null，status=RECALLED，前端据此渲染「撤回」占位。
 */
@Data
public class MessageView implements Serializable {

    private Long id;

    private Long conversationId;

    private Long senderId;

    private String content;

    private String msgType;

    private String status;

    private Long replyToId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
