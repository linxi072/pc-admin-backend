package com.acme.scaffold.common.audit;

/**
 * 操作审计服务。实现可基于数据库、消息队列或独立审计存储。
 */
public interface AuditLogService {

    /**
     * 异步写入审计记录（事务提交后落库，避免阻塞主流程）。
     */
    void saveAsync(AuditRecord record);
}
