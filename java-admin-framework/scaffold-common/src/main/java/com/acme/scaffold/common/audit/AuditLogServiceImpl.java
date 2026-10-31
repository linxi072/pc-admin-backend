package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.common.api.dto.KeysetPageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.domain.SysOperationLogDO;
import com.acme.scaffold.common.audit.dto.AuditLogQuery;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooq.paging.JooqKeysetPager;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 审计日志实现：优先在事务提交后异步落库；无事务时直接异步写入。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 *
 * <p>查询侧对齐设计方案 §20：支持按 traceId 追溯链路，且对外视图经
 * {@link com.acme.scaffold.common.sensitive.SensitiveMasker} 脱敏。
 * 注意 {@code sys_operation_log} <b>没有 deleted 列</b>（审计数据不允许逻辑删除），
 * 故查询不使用 {@link JooqWriters#notDeleted}，归档由独立运维流程负责。
 */
@Slf4j
@Service
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

    /** 审计专用线程池（{@code config.observability.ObservabilityConfig#auditExecutor}）。 */
    private final Executor auditExecutor;

    /** 摘要截断的 JSON 合法性校验（两个摘要列均为 JSON 类型，截断不得破坏语法）。 */
    private final ObjectMapper objectMapper;

    /**
     * 显式构造器：{@code Executor} 在容器中存在多个实现，需按 Bean 名精确注入审计线程池。
     */
    public AuditLogServiceImpl(DSLContext dsl, @Qualifier("auditExecutor") Executor auditExecutor,
                               ObjectMapper objectMapper) {
        this.dsl = dsl;
        this.auditExecutor = auditExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存审计记录：业务事务提交后才落库，未提交（回滚）则不写。
     *
     * <p><b>为什么本方法不再标注 {@code @Async}</b>：{@code @Async} 会把方法体切到线程池线程执行，
     * 而 {@link TransactionSynchronizationManager} 是<b>线程绑定</b>的——切换线程后
     * {@code isSynchronizationActive()} 恒为 false，「事务提交后再写」的分支永远走不到，
     * 结果是业务尚未提交（甚至随后回滚）审计就已落库，审计里留下「假成功」记录。
     * 因此改为在<b>调用方线程</b>判定事务状态，确认提交后再把插入任务投递给线程池。</p>
     */
    @Override
    public void saveAsync(AuditRecord record) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(record);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        log.debug("业务事务未提交(status={})，跳过审计落库: module={} type={}",
                                status, record.getModuleCode(), record.getOperationType());
                    }
                }
            });
        } else {
            submit(record);
        }
    }

    /**
     * 投递插入任务到审计线程池；线程池拒绝时退化为同步写入——审计属于合规留痕，
     * 宁可占用一次调用方线程，也不能因为队列满而整条丢失。
     */
    private void submit(AuditRecord record) {
        try {
            auditExecutor.execute(() -> doInsert(record));
        } catch (Exception e) {
            log.warn("审计线程池提交失败，退化为同步写入: module={}", record.getModuleCode(), e);
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
    public KeysetPageResult<AuditLogView> keysetPage(AuditLogQuery query, String cursor, boolean sensitiveVisible) {
        // keyset 不读取 query 的排序字段，固定按 (occurred_at DESC, id DESC) 排序，
        // 仅复用筛选条件；排序键含 id 作为 tie-breaker，保证顺序唯一确定。
        KeysetPageQuery q = new KeysetPageQuery(query.size(), cursor);
        Field<?>[] keyFields = new Field<?>[]{
                JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class),
                JooqTables.SYS_OPERATION_LOG.field("id", Long.class)
        };
        boolean[] ascending = {false, false}; // occurred_at DESC, id DESC
        KeysetPageResult<SysOperationLogDO> raw = JooqKeysetPager.query(dsl, JooqTables.SYS_OPERATION_LOG,
                SysOperationLogDO.class, conditions(query), q,
                l -> new Object[]{l.getOccurredAt(), l.getId()}, keyFields, ascending);
        List<AuditLogView> views = raw.records().stream()
                .map(l -> AuditLogView.of(l, sensitiveVisible))
                .toList();
        return KeysetPageResult.of(raw.size(), views, raw.nextCursor());
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
    public AuditLogSummary summary(AuditLogQuery query, boolean sensitiveVisible) {
        Condition cond = conditions(query);
        long total = JooqWriters.count(dsl, JooqTables.SYS_OPERATION_LOG, cond);
        if (total == 0) {
            // 空区间也回传脱敏可见标识，保证前端徽标状态与列表一致
            return new AuditLogSummary(0L, 0L, 0L, 0L, 0L, 0L, sensitiveVisible);
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
        return new AuditLogSummary(total, successCount, total - successCount, slowCount, avg, max, sensitiveVisible);
    }

    @Override
    public int purgeBefore(LocalDateTime cutoff) {
        if (cutoff == null) {
            return 0;
        }
        try {
            // sys_operation_log 无 deleted 列，过期数据直接物理删除（与留存清理语义一致）
            return dsl.deleteFrom(JooqTables.SYS_OPERATION_LOG.table())
                    .where(JooqTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).lt(cutoff))
                    .execute();
        } catch (Exception e) {
            log.error("清理过期审计日志失败 cutoff={}", cutoff, e);
            return 0;
        }
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
        if (s.length() <= max) {
            return s;
        }
        // 摘要列为 JSON 类型：截断点可能落在转义序列中间产生非法 JSON（MySQL 3140），
        // 校验失败时降级为合法字符串标量，保证审计不因摘要超长而静默丢失
        String truncated = s.substring(0, max);
        try {
            objectMapper.readTree(truncated);
            return truncated;
        } catch (Exception e) {
            return "\"[truncated]\"";
        }
    }
}
