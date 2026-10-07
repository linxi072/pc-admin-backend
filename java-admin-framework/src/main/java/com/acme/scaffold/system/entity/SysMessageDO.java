package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 站内信发件主表：一次发送动作 = 一条记录。
 * 接收人明细见 {@link SysMessageReceiptDO}。
 */
@Data
public class SysMessageDO {

    private Long id;

    private Long tenantId = 0L;
    private String title;
    private String content;
    /** NOTICE(通知) / ALERT(告警) / SYSTEM(系统) */
    private String msgType = "NOTICE";
    private Long senderId;
    /** 批量发送时的角色筛选快照（逗号分隔），便于审计「当时按什么范围发的」 */
    private String filterRoleIds;
    /** 批量发送时的部门筛选快照（逗号分隔） */
    private String filterOrgIds;
    /** 单条发送时的显式接收人ID（逗号分隔） */
    private String receiverIds;
    private Integer totalCount = 0;
    private Integer readCount = 0;
    private LocalDateTime sentAt;

    private Integer version = 0;

    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
