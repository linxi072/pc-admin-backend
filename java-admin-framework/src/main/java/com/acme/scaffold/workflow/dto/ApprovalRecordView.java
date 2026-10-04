package com.acme.scaffold.workflow.dto;

import java.time.LocalDateTime;

public record ApprovalRecordView(String operationId, String action, Long operatorUserId, Long fromUserId,
                                Long toUserId, String opinion, LocalDateTime occurredAt) {
}
