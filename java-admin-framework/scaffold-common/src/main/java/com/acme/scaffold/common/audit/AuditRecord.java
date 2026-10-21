package com.acme.scaffold.common.audit;

import java.time.Instant;

/**
 * 一条审计记录的内容载体（不含持久化注解，便于跨存储）。
 */
public class AuditRecord {

    private String moduleCode;
    private String operationType;
    private String operationName;
    private Long operatorId;
    private String operatorName;
    private String requestMethod;
    private String requestPath;
    private String requestSummary;
    private String resultSummary;
    private String resultCode;
    private boolean success;
    private long durationMs;
    private String ipAddress;
    private String traceId;
    private Instant occurredAt = Instant.now();

    public static AuditRecord of(String module, String type, String name) {
        AuditRecord r = new AuditRecord();
        r.moduleCode = module;
        r.operationType = type;
        r.operationName = name;
        return r;
    }

    public AuditRecord operator(Long id, String name) {
        this.operatorId = id;
        this.operatorName = name;
        return this;
    }

    public AuditRecord request(String method, String path, String summary) {
        this.requestMethod = method;
        this.requestPath = path;
        this.requestSummary = summary;
        return this;
    }

    public AuditRecord result(String summary, String code, boolean success) {
        this.resultSummary = summary;
        this.resultCode = code;
        this.success = success;
        return this;
    }

    public AuditRecord meta(String ip, String traceId, long durationMs) {
        this.ipAddress = ip;
        this.traceId = traceId;
        this.durationMs = durationMs;
        return this;
    }

    public String getModuleCode() { return moduleCode; }
    public String getOperationType() { return operationType; }
    public String getOperationName() { return operationName; }
    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getRequestMethod() { return requestMethod; }
    public String getRequestPath() { return requestPath; }
    public String getRequestSummary() { return requestSummary; }
    public String getResultSummary() { return resultSummary; }
    public String getResultCode() { return resultCode; }
    public boolean isSuccess() { return success; }
    public long getDurationMs() { return durationMs; }
    public String getIpAddress() { return ipAddress; }
    public String getTraceId() { return traceId; }
    public Instant getOccurredAt() { return occurredAt; }
}
