package com.acme.scaffold.common.audit;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 审计日志实现：优先在事务提交后异步落库；无事务时直接异步写入。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final DSLContext dsl;

    @Async("auditExecutor")
    @Override
    public void saveAsync(AuditRecord record) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doInsert(record);
                }
            });
        } else {
            doInsert(record);
        }
    }

    private void doInsert(AuditRecord record) {
        try {
            SysOperationLogDO entity = new SysOperationLogDO();
            entity.setTenantId(0L);
            entity.setModuleCode(record.getModuleCode());
            entity.setOperationType(record.getOperationType());
            entity.setOperationName(record.getOperationName());
            entity.setOperatorId(record.getOperatorId());
            entity.setOperatorName(record.getOperatorName());
            entity.setRequestMethod(record.getRequestMethod());
            entity.setRequestPath(record.getRequestPath());
            entity.setRequestSummary(truncate(record.getRequestSummary(), 2000));
            entity.setResultSummary(truncate(record.getResultSummary(), 2000));
            entity.setResultCode(record.getResultCode());
            entity.setDurationMs(record.getDurationMs());
            entity.setIpAddress(record.getIpAddress());
            entity.setTraceId(record.getTraceId());
            entity.setSuccess(record.isSuccess() ? 1 : 0);
            JooqWriters.insert(dsl, JooqTables.SYS_OPERATION_LOG, entity);
        } catch (Exception e) {
            log.error("写入审计日志失败 module={} type={}", record.getModuleCode(), record.getOperationType(), e);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
