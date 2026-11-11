package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.api.dto.KeysetPageQuery;
import com.acme.scaffold.common.api.dto.KeysetPageResult;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.audit.domain.SysOperationLogDO;
import com.acme.scaffold.common.audit.dto.AuditLogQuery;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.Field;
import com.acme.scaffold.mybatis.dsl.KeysetPager;
import com.acme.scaffold.mybatis.dsl.Record;
import com.acme.scaffold.mybatis.dsl.SqlSorts;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

/**
 * 操作审计服务（单类实现）：优先在事务提交后异步落库；无事务时直接异步写入，
 * 实现可基于数据库、消息队列或独立审计存储。持久层使用自研 MyBatis 风格 DSL。
 *
 * <p>职责分两部分：
 * <ul>
 *   <li><b>写入</b>：{@link #saveAsync} 由 {@link AuditAspect} 在切面里调用，事务提交后落库；</li>
 *   <li><b>查询</b>：面向管理员的审计追溯能力（设计方案 §20「审计日志可按 traceId 查询且无敏感字段」）。
 *       查询结果一律经 {@link com.acme.scaffold.common.sensitive.SensitiveMasker} 脱敏后再对外返回。</li>
 * </ul>
 *
 * <p>查询侧对齐设计方案 §20：支持按 traceId 追溯链路。
 * 注意 {@code sys_operation_log} <b>没有 deleted 列</b>（审计数据不允许逻辑删除），
 * 故查询不使用 {@link SqlWriters#notDeleted}，归档由独立运维流程负责。
 */
@Slf4j
@Service
public class AuditLogService {

    /** 审计日志可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> AUDIT_SORT_FIELDS = SqlSorts.whitelist(
            "id", "id",
            "moduleCode", "module_code",
            "operationType", "operation_type",
            "operatorId", "operator_id",
            "durationMs", "duration_ms",
            "success", "success",
            "occurredAt", "occurred_at");

    /** 「最近动态」面板单次最多返回条数（与 WorkbenchController 的入参上限保持一致）。 */
    private static final int RECENT_ACTIVITIES_MAX = 50;

    private final DSLContext dsl;

    /** 审计专用线程池（{@code config.observability.ObservabilityConfig#auditExecutor}）。 */
    private final Executor auditExecutor;

    /** 摘要截断的 JSON 合法性校验（两个摘要列均为 JSON 类型，截断不得破坏语法）。 */
    private final ObjectMapper objectMapper;

    /** 事务内审计缓冲：同一业务事务内的多次 {@code saveAsync} 先入此缓冲，
     *  事务提交后合并为单次批量插入（{@link SqlWriters#batchInsert}），消除 N+1 同构 INSERT 告警。 */
    private static final ThreadLocal<List<AuditRecord>> TX_BUFFER = ThreadLocal.withInitial(ArrayList::new);

    /**
     * 显式构造器：{@code Executor} 在容器中存在多个实现，需按 Bean 名精确注入审计线程池。
     */
    public AuditLogService(DSLContext dsl, @Qualifier("auditExecutor") Executor auditExecutor,
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
    /**
     * 异步写入审计记录（事务提交后落库，避免阻塞主流程）。
     */
    public void saveAsync(AuditRecord record) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 同一业务事务内的多条审计记录先缓冲到线程局部，事务提交后<b>合并为单次批量插入</b>，
            // 消除 N+1（同一事务内多次 @AuditOperation 各注册 afterCommit 会触发同构 INSERT 告警）。
            List<AuditRecord> buffer = TX_BUFFER.get();
            boolean first = buffer.isEmpty();
            buffer.add(record);
            if (first) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        flushBuffer();
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != STATUS_COMMITTED) {
                            log.debug("业务事务未提交(status={})，跳过审计落库: module={} type={}",
                                    status, record.getModuleCode(), record.getOperationType());
                        }
                        TX_BUFFER.remove();
                    }
                });
            }
        } else {
            submit(List.of(record));
        }
    }

    /**
     * 事务提交后：把缓冲的审计记录合并为单次批量插入并投递给审计线程池。
     * 线程池拒绝时退化为同步写入——审计属于合规留痕，宁可占用调用方线程也不能整条丢失。
     */
    private void flushBuffer() {
        try {
            List<AuditRecord> buffer = TX_BUFFER.get();
            if (buffer.isEmpty()) {
                return;
            }
            List<AuditRecord> snapshot = new ArrayList<>(buffer);
            submit(snapshot);
        } finally {
            TX_BUFFER.remove();
        }
    }

    /**
     * 投递批量插入任务到审计线程池；线程池拒绝时退化为同步写入。
     */
    private void submit(List<AuditRecord> records) {
        try {
            auditExecutor.execute(() -> doInsertBatch(records));
        } catch (Exception e) {
            log.warn("审计线程池提交失败，退化为同步写入: size={}", records.size(), e);
            doInsertBatch(records);
        }
    }

    /**
     * 批量落库：多条记录合并为单次 INSERT（{@link SqlWriters#batchInsert}），
     * 单条记录退化为 {@link #doInsert} 与原行为一致。
     */
    private void doInsertBatch(List<AuditRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        if (records.size() == 1) {
            doInsert(records.get(0));
            return;
        }
        try {
            List<SysOperationLogDO> entities = records.stream().map(this::toEntity).toList();
            SqlWriters.batchInsert(dsl, SqlTables.SYS_OPERATION_LOG, entities);
        } catch (Exception e) {
            log.error("批量写入审计日志失败 size={}", records.size(), e);
        }
    }

    private void doInsert(AuditRecord record) {
        try {
            SqlWriters.insert(dsl, SqlTables.SYS_OPERATION_LOG, toEntity(record));
        } catch (Exception e) {
            log.error("写入审计日志失败 module={} type={}", record.getModuleCode(), record.getOperationType(), e);
        }
    }

    /** 将审计承载对象映射为持久化 DO（含摘要 JSON 合法性截断）。 */
    private SysOperationLogDO toEntity(AuditRecord record) {
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
        return entity;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 分页查询审计日志。
     *
     * @param query            查询条件（构造期已完成时间区间等校验）
     * @param sensitiveVisible 是否回传未脱敏摘要，由 Controller 依据 {@code system:audit:sensitive} 权限决定
     */
    public PageResult<AuditLogView> query(AuditLogQuery query, boolean sensitiveVisible) {
        var pageQuery = query.toPageQuery();
        PageResult<SysOperationLogDO> page = SqlWriters.page(dsl, SqlTables.SYS_OPERATION_LOG,
                SysOperationLogDO.class, conditions(query), pageQuery,
                SqlSorts.resolve(SqlTables.SYS_OPERATION_LOG, pageQuery, AUDIT_SORT_FIELDS,
                        // 默认：发生时间倒序（最新在前），id 兜底保证翻页稳定
                        SqlTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).desc(),
                        SqlTables.SYS_OPERATION_LOG.field("id", Long.class).desc()));

        List<AuditLogView> views = page.records().stream()
                .map(l -> AuditLogView.of(l, sensitiveVisible))
                .toList();
        return new PageResult<>(page.page(), page.size(), page.total(), views);
    }

    /**
     * keyset 游标分页（深翻页优化）：固定按 (occurred_at DESC, id DESC) 排序，
     * 游标由服务端基于上一页末条记录的排序键生成，避免 offset 分页随偏移量退化。
     *
     * <p>仅复用 {@link AuditLogQuery} 的筛选部分（排序由 keyset 固定为主键序）；
     * 出参不含 total（keyset 不廉价计算总数），以 {@code nextCursor} 表示是否还有下一页。
     *
     * @param query            查询条件（仅筛选部分生效，排序被忽略）
     * @param cursor           不透明游标（首页为空串）
     * @param sensitiveVisible 是否回传未脱敏摘要，由 Controller 依据 {@code system:audit:sensitive} 权限决定
     */
    public KeysetPageResult<AuditLogView> keysetPage(AuditLogQuery query, String cursor, boolean sensitiveVisible) {
        // keyset 不读取 query 的排序字段，固定按 (occurred_at DESC, id DESC) 排序，
        // 仅复用筛选条件；排序键含 id 作为 tie-breaker，保证顺序唯一确定。
        KeysetPageQuery q = new KeysetPageQuery(query.size(), cursor);
        Field<?>[] keyFields = new Field<?>[]{
                SqlTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class),
                SqlTables.SYS_OPERATION_LOG.field("id", Long.class)
        };
        boolean[] ascending = {false, false}; // occurred_at DESC, id DESC
        KeysetPageResult<SysOperationLogDO> raw = KeysetPager.query(dsl, SqlTables.SYS_OPERATION_LOG,
                SysOperationLogDO.class, conditions(query), q,
                l -> new Object[]{l.getOccurredAt(), l.getId()}, keyFields, ascending);
        List<AuditLogView> views = raw.records().stream()
                .map(l -> AuditLogView.of(l, sensitiveVisible))
                .toList();
        return KeysetPageResult.of(raw.size(), views, raw.nextCursor());
    }

    /**
     * 按 traceId 查询一次请求的完整操作链路（按发生时间正序）。
     *
     * @throws com.acme.scaffold.common.exception.BusinessException traceId 为空时抛 400
     */
    public List<AuditLogView> findByTraceId(String traceId, boolean sensitiveVisible) {
        if (!StringUtils.hasText(traceId)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "traceId 不能为空");
        }
        // 链路追溯按时间正序，便于看完整调用顺序
        List<SysOperationLogDO> logs = SqlWriters.fetchList(dsl, SqlTables.SYS_OPERATION_LOG,
                SysOperationLogDO.class,
                SqlTables.SYS_OPERATION_LOG.field("trace_id", String.class).eq(traceId.trim()),
                SqlTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).asc(),
                SqlTables.SYS_OPERATION_LOG.field("id", Long.class).asc());
        return logs.stream().map(l -> AuditLogView.of(l, sensitiveVisible)).toList();
    }

    /**
     * 查询指定操作人的最近若干条审计记录（工作台「最近动态」面板）。
     *
     * <p>与 {@link #query} 的唯一区别是<b>不计算总条数</b>：该面板只展示最近 N 条，
     * 不需要 total。而 {@code count(*)} 在千万行级审计表上即使命中索引也要数完匹配行，
     * 叠加 {@code ORDER BY occurred_at, id} 回表排序，曾使该端点耗时 11.9s 并越过前端
     * 15s 超时阈值（E2E F1）。本方法改为单条 {@code ... ORDER BY ... LIMIT n} 查询。</p>
     *
     * @param operatorId       操作人主键；{@code null} 表示不限操作人（全局最近记录）
     * @param limit            条数，合法区间 1..50，越界按边界钳制
     * @param sensitiveVisible 是否回传未脱敏摘要
     */
    public List<AuditLogView> recentActivities(Long operatorId, int limit, boolean sensitiveVisible) {
        int size = Math.min(Math.max(limit, 1), RECENT_ACTIVITIES_MAX);
        Condition cond = operatorId == null
                ? DSL.trueCondition()
                : SqlTables.SYS_OPERATION_LOG.field("operator_id", Long.class).eq(operatorId);
        // 不做 count：面板只需最近 N 条，count(*) 在千万行审计表上是纯浪费（见接口注释 F1）
        List<SysOperationLogDO> logs = SqlWriters.fetchTop(dsl, SqlTables.SYS_OPERATION_LOG,
                SysOperationLogDO.class, cond, size,
                SqlTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).desc(),
                SqlTables.SYS_OPERATION_LOG.field("id", Long.class).desc());
        return logs.stream().map(l -> AuditLogView.of(l, sensitiveVisible)).toList();
    }

    /**
     * 按条件统计摘要（总数 / 成功 / 失败 / 慢操作 / 平均与最大耗时）。
     *
     * @param query            查询条件（构造期已完成时间区间等校验）
     * @param sensitiveVisible 当前主体是否持有 {@code system:audit:sensitive} 权限，回传进摘要供前端渲染脱敏徽标
     */
    public AuditLogSummary summary(AuditLogQuery query, boolean sensitiveVisible) {
        var t = SqlTables.SYS_OPERATION_LOG;
        Condition cond = conditions(query);
        Field<Long> duration = t.field("duration_ms", Long.class);

        // 五项指标合并为单条聚合 SQL。
        // 原实现是 4 次 count(*) + 1 次 avg/max = 5 次全表扫描，在 1200 万行的
        // sys_operation_log 上耗时 16.3s，直接越过前端 15s 超时并返回 500（E2E F3）。
        //
        // avg/max 的字段类型显式声明为 BigDecimal 而非 Long：duration_ms 在 MySQL 中为
        // BIGINT UNSIGNED，jOOQ 若以 Long 读取 max() 会走 rs.getLong 的无符号转换分支并抛
        //   Error while reading field: max("duration_ms"), at JDBC index: 2
        // 改为 BigDecimal（rs.getBigDecimal）后与列的无符号性解耦，MySQL/H2 行为一致。
        Record agg = dsl.select(
                        DSL.count().as("total"),
                        DSL.sum(DSL.when(t.field("success", Integer.class).eq(1), 1).otherwise(0))
                                .as("success_count"),
                        DSL.sum(DSL.when(duration.gt(AuditLogSummary.SLOW_THRESHOLD_MS), 1).otherwise(0))
                                .as("slow_count"),
                        DSL.field("avg({0})", BigDecimal.class, duration).as("avg_duration"),
                        DSL.field("max({0})", BigDecimal.class, duration).as("max_duration"))
                .from(t.table())
                .where(cond)
                .fetchOne();
        if (agg == null) {
            // 聚合查询恒返回一行；此处仅作防御，空区间也回传脱敏可见标识，保证前端徽标状态与列表一致
            return new AuditLogSummary(0L, 0L, 0L, 0L, 0L, 0L, sensitiveVisible);
        }
        // 按下标取原始对象再统一收敛：count(*) 在不同方言下可能是 Integer/Long，
        // sum/avg 可能是 DECIMAL/BigDecimal，避免逐个字段声明类型导致的读取不匹配
        long total = numberOf(agg, 0);
        long successCount = numberOf(agg, 1);
        long slowCount = numberOf(agg, 2);
        long avg = numberOf(agg, 3);
        long max = numberOf(agg, 4);
        return new AuditLogSummary(total, successCount, total - successCount, slowCount, avg, max, sensitiveVisible);
    }

    /** 读取聚合一列并收敛为 long（null / 非数值一律按 0，避免统计口径外泄脏值）。 */
    private static long numberOf(Record record, int index) {
        Object value = record.get(index);
        return value instanceof Number n ? n.longValue() : 0L;
    }

    /**
     * 清理发生时间早于 {@code cutoff} 的审计记录（留存清理，M20 增强）。
     *
     * @param cutoff 截止时间（不含）；{@code null} 时调用方应跳过
     * @return 删除的记录条数
     */
    public int purgeBefore(LocalDateTime cutoff) {
        if (cutoff == null) {
            return 0;
        }
        try {
            // sys_operation_log 无 deleted 列，过期数据直接物理删除（与留存清理语义一致）
            return dsl.deleteFrom(SqlTables.SYS_OPERATION_LOG.table())
                    .where(SqlTables.SYS_OPERATION_LOG.field("occurred_at", LocalDateTime.class).lt(cutoff))
                    .execute();
        } catch (Exception e) {
            log.error("清理过期审计日志失败 cutoff={}", cutoff, e);
            return 0;
        }
    }

    /** 把查询条件翻译为 jOOQ 条件集合；无过滤条件时返回恒真。 */
    private Condition conditions(AuditLogQuery query) {
        List<Condition> conds = new ArrayList<>();
        var t = SqlTables.SYS_OPERATION_LOG;
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
        // 摘要列为 JSON 类型：截断不得破坏语法（MySQL 3140）。委托 JsonTruncator 做
        // 三级降级（原样保留 → 紧凑化/结构化裁剪 → 文本兜底包装为字符串标量），
        // 保证超长摘要仍为合法 JSON，不再整段丢失原文（F4）。
        return JsonTruncator.safeTruncate(s, max, objectMapper);
    }
}
