package com.acme.scaffold.im.message.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息实体（对应 im_message，DDL 见 V75__im.sql）。
 * 逻辑删除（deleted），乐观锁（version）。
 */
@Data
public class ImMessageDO {

    private Long id;

    private Long tenantId;

    private Long conversationId;

    private Long senderId;

    /** 消息正文（TEXT，撤回后置空）。 */
    private String content;

    /** TEXT / IMAGE / FILE / SYSTEM。 */
    private String msgType;

    /** SENT / RECALLED。 */
    private String status;

    /** 回复的消息主键。 */
    private Long replyToId;

    private Integer version;

    private Integer deleted;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
