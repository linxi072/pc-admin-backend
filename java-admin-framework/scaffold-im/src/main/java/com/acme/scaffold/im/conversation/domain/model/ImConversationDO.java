package com.acme.scaffold.im.conversation.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 会话实体（对应 im_conversation，DDL 见 V75__im.sql）。
 * 逻辑删除（deleted），乐观锁（version）。
 */
@Data
public class ImConversationDO {

    private Long id;

    private Long tenantId;

    /** P2P / GROUP。 */
    private String conversationType;

    /** 群组名称；点对点会话为 null（前端按对方用户解析展示）。 */
    private String title;

    /** 群主用户主键。 */
    private Long ownerId;

    private String status;

    private Integer version;

    private Integer deleted;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
