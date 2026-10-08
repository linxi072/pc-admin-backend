package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqSorts;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 审计日志实现：优先在事务提交后异步落库；无事务时直接异步写入。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 *
 * <p>查询侧对齐设计方案 §20：支持按 traceId 追溯链路，且对外视图经 {@link SensitiveMasker} 脱敏。
 * 注意 {@code sys_operation_log} <b>没有 deleted 列</b>（审计数据不允许逻辑删除），
 * 故查询不使用 {@link JooqWriters#notDeleted}，归档由独立运维流程负责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    /** 审计日志可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> AUDIT_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "moduleCode", "module_code",
            "operationType", "operation_type",
            "operatorId", "operator_id",
            "durationMs", "duration_ms",
            "success", "success",
            "occurredAt", "occurred_at");

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

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Override
    public PageResult<AuditLogView> query(AuditLogQuery query, boolean sensitiveVisible) {
        var pageQuery = query.toPageQuery();
        PageResult<SysOperationLogDO> page = JooqWriters.page(dsl, JooqTables.SYS_OPERATION_LOG,
                SysOperationLogDO.class, conditions(query), pageQuery,
                JooqSorts.resolve(JooqTables.SYS_OPERATION_LOG, pageQuery, AUDIT_SORT_FIELDS,
                        // 默认：发生时间倒序（最新在前），id 兜底保证翻页稳定
                        JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).desc(),
                        JooqTables.SYS_OPERATION_LOG.field("id", Long.class).desc()));

        List<AuditLogView> views = page.records().stream()
                .map(l -> AuditLogView.of(l, sensitiveVisible))
                .toList();
        return new PageResult<>(page.page(), page.size(), page.total(), views);
    }

    @Override
    public List<AuditLogView> findByTraceId(String traceId, boolean sensitiveVisible) {
        if (!StringUtils.hasText(traceId)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "traceId 不能为空");
        }
        // 链路追溯按时间正序，便于看完整调用顺序
        List<SysOperationLogDO> logs = JooqWriters.fetchList(dsl, JooqTables.SYS_OPERATION_LOG,
                SysOperationLogDO.class,
                JooqTables.SYS_OPERATION_LOG.field("trace_id", String.class).eq(traceId.trim()),
                JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).asc(),
                JooqTables.SYS_OPERATION_LOG.field("id", Long.class).asc());
        return logs.stream().map(l -> AuditLogView.of(l, sensitiveVisible)).toList();
    }

    @Override
    public AuditLogSummary summary(AuditLogQuery query) {
        Condition cond = conditions(query);
        long total = JooqWriters.count(dsl, JooqTables.SYS_OPERATION_LOG, cond);
        if (total == 0) {
            return AuditLogSummary.empty();
        }
        long successCount = JooqWriters.count(dsl, JooqTables.SYS_OPERATION_LOG,
                DSL.and(cond, JooqTables.SYS_OPERATION_LOG.field("success", Integer.class).eq(1)));
        long slowCount = JooqWriters.count(dsl, JooqTables.SYS_OPERATION_LOG,
                DSL.and(cond, JooqTables.SYS_OPERATION_LOG.field("duration_ms", Long.class)
                        .gt(AuditLogSummary.SLOW_THRESHOLD_MS)));

        Record2<BigDecimal, Long> agg = dsl
                .select(DSL.avg(JooqTables.SYS_OPERATION_LOG.field("duration_ms", Long.class)),
                        DSL.max(JooqTables.SYS_OPERATION_LOG.field("duration_ms", Long.class)))
                .from(JooqTables.SYS_OPERATION_LOG.table())
                .where(cond)
                .fetchOne();
        long avg = 0L;
        long max = 0L;
        if (agg != null) {
            // avg 可能返回 null（全表 duration_ms 为 NULL 时），max 同理
            avg = agg.value1() == null ? 0L : agg.value1().longValue();
            max = agg.value2() == null ? 0L : agg.value2();
        }
        return new AuditLogSummary(total, successCount, total - successCount, slowCount, avg, max);
    }

    /** 把查询条件翻译为 jOOQ 条件集合；无过滤条件时返回恒真。 */
    private Condition conditions(AuditLogQuery query) {
        List<Condition> conds = new ArrayList<>();
        var t = JooqTables.SYS_OPERATION_LOG;
        if (StringUtils.hasText(query.moduleCode())) {
            conds.add(t.field("module_code", String.class).eq(query.moduleCode()));
        }
        if (StringUtils.hasText(query.operationType())) {
            conds.add(t.field("operation_type", String.class).eq(query.operationType()));
        }
        if (query.operatorId() != null) {
            conds.add(t.field("operator_id", Long.class).eq(query.operatorId()));
        }
        if (StringUtils.hasText(query.operatorName())) {
            conds.add(t.field("operator_name", String.class).like("%" + query.operatorName() + "%"));
        }
        if (query.success() != null) {
            conds.add(t.field("success", Integer.class).eq(Boolean.TRUE.equals(query.success()) ? 1 : 0));
        }
        if (StringUtils.hasText(query.traceId())) {
            conds.add(t.field("trace_id", String.class).eq(query.traceId()));
        }
        if (StringUtils.hasText(query.keyword())) {
            String kw = "%" + query.keyword() + "%";
            conds.add(DSL.or(t.field("operation_name", String.class).like(kw),
                    t.field("request_path", String.class).like(kw)));
        }
        if (query.minDurationMs() != null) {
            conds.add(t.field("duration_ms", Long.class).ge(query.minDurationMs()));
        }
        if (query.startTime() != null) {
            conds.add(t.field("occurred_at", LocalDateTime.class).ge(query.startTime()));
        }
        if (query.endTime() != null) {
            conds.add(t.field("occurred_at", LocalDateTime.class).le(query.endTime()));
        }
        return conds.isEmpty() ? DSL.trueCondition() : DSL.and(conds);
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
