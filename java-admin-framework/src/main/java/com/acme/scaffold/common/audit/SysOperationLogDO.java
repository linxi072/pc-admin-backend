package com.acme.scaffold.common.audit;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志持久化对象。原则上不可更新；归档/删除需独立权限与审批。
 */
@Data
public class SysOperationLogDO {

    private Long id;

    private Long tenantId = 0L;

    private String moduleCode;

    private String operationType;

    private String operationName;

    private Long operatorId;

    private String operatorName;

    private String requestMethod;

    private String requestPath;

    /** 仅存白名单字段，已脱敏。 */
    private String requestSummary;

    private String resultSummary;

    private String resultCode;

    private Long durationMs = 0L;

    private String ipAddress;

    private String traceId;

    private Integer success = 1;

    private LocalDateTime occurredAt;
}
