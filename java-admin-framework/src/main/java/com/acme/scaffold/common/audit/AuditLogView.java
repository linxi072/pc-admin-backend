package com.acme.scaffold.common.audit;

import java.time.LocalDateTime;

/**
 * 审计日志对外视图。
 *
 * <p><b>与持久化对象的差异</b>：摘要与 IP 一律经 {@link SensitiveMasker} 处理。
 * 仅当调用方持有 {@code system:audit:sensitive} 权限时才回传原文
 * （由 Controller 依据当前主体权限决定 {@code sensitiveVisible}）。
 */
public record AuditLogView(Long id, String moduleCode, String operationType, String operationName,
                           Long operatorId, String operatorName, String requestMethod, String requestPath,
                           String requestSummary, String resultSummary, String resultCode, Long durationMs,
                           String ipAddress, String traceId, Boolean success, LocalDateTime occurredAt) {

    public static AuditLogView of(SysOperationLogDO log, boolean sensitiveVisible) {
        if (log == null) {
            return null;
        }
        if (sensitiveVisible) {
            // 高权限视图：仅摘要保留原文，IP 仍掩码（IP 属个人轨迹信息，默认不暴露）
            return new AuditLogView(log.getId(), log.getModuleCode(), log.getOperationType(),
                    log.getOperationName(), log.getOperatorId(), log.getOperatorName(), log.getRequestMethod(),
                    log.getRequestPath(), log.getRequestSummary(), log.getResultSummary(), log.getResultCode(),
                    log.getDurationMs(), SensitiveMasker.maskIp(log.getIpAddress()), log.getTraceId(),
                    log.getSuccess() != null && log.getSuccess() == 1, log.getOccurredAt());
        }
        return new AuditLogView(log.getId(), log.getModuleCode(), log.getOperationType(), log.getOperationName(),
                log.getOperatorId(), log.getOperatorName(), log.getRequestMethod(), log.getRequestPath(),
                SensitiveMasker.maskSummary(log.getRequestSummary()),
                SensitiveMasker.maskSummary(log.getResultSummary()), log.getResultCode(), log.getDurationMs(),
                SensitiveMasker.maskIp(log.getIpAddress()), log.getTraceId(),
                log.getSuccess() != null && log.getSuccess() == 1, log.getOccurredAt());
    }
}
