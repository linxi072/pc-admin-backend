package com.acme.scaffold.workflow.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;

/**
 * 审批记录视图。用户 id 为 18 位雪花 ID，超出 JS Number.MAX_SAFE_INTEGER，序列化为字符串防精度丢失。
 */
public record ApprovalRecordView(String operationId, String action,
                                 @JsonSerialize(using = ToStringSerializer.class) Long operatorUserId,
                                 @JsonSerialize(using = ToStringSerializer.class) Long fromUserId,
                                 @JsonSerialize(using = ToStringSerializer.class) Long toUserId,
                                 String opinion, LocalDateTime occurredAt) {
}
