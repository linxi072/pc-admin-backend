package com.acme.scaffold.common.idempotency;

import com.acme.scaffold.common.idempotency.domain.IdempotencyRecord;
import com.acme.scaffold.common.idempotency.rules.IdempotencyRecordRules;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 幂等记录读写。
 *
 * <p><b>关键设计：所有写操作都在 {@code REQUIRES_NEW} 独立事务中执行。</b>
 * 若与业务共用事务，业务回滚会把「已登记」的幂等记录一并抹掉，
 * 客户端重试就会变成一次真实的新执行——幂等形同虚设。
 * 独立事务的代价是：业务失败但记录已落 SUCCESS 的窗口极小（仅在提交后写回阶段失败），
 * 此时由 markFailed 覆盖状态，保证最终一致。
 *
 * <p>所有按 (key, scope) 的读写均经 {@code SqlWriters.withTenant} 施加租户条件，强隔离下不同租户
 * 使用相同 key/scope 不会互相覆盖状态、响应快照或误删在途记录。</p>
 */
@Slf4j
@Service
public class IdempotencyService {

    /** 响应快照最大长度，超出则不存（避免大对象撑爆表）。 */
    private static final int MAX_SNAPSHOT_CHARS = 8000;

    private final DSLContext dsl;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate requiresNew;

    public IdempotencyService(DSLContext dsl, ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.dsl = dsl;
        this.objectMapper = objectMapper;
        this.requiresNew = new TransactionTemplate(transactionManager);
        this.requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 登记本次请求。
     *
     * @return 命中动作与（可能存在的）首次响应快照
     */
    public AcquireResult acquire(String key, String scope, Long userId, String fingerprint, int ttlSeconds) {
        return requiresNew.execute(status -> doAcquire(key, scope, userId, fingerprint, ttlSeconds));
    }

    /** 业务成功：写入响应快照，后续同 key 请求重放该快照。 */
    public void markSuccess(String key, String scope, Object response) {
        String snapshot = toSnapshot(response);
        requiresNew.execute(status -> {
            var t = SqlTables.SYS_IDEMPOTENCY_RECORD;
            dsl.update(t.table())
                    .set(t.field("status", String.class), IdempotencyStatus.SUCCESS.name())
                    .set(t.field("response_snapshot", String.class), snapshot)
                    .set(t.field("error_code", String.class), (String) null)
                    .set(t.field("error_message", String.class), (String) null)
                    .set(t.field("updated_at", LocalDateTime.class), LocalDateTime.now())
                    .where(keyCondition(key, scope))
                    .execute();
            return null;
        });
    }

    /**
     * 业务失败（{@link com.acme.scaffold.common.exception.BusinessException}）：
     * 记为 FAILED 并保留错误码，允许客户端修正后用同 key 重试。
     */
    public void markFailed(String key, String scope, String errorCode, String errorMessage) {
        requiresNew.execute(status -> {
            var t = SqlTables.SYS_IDEMPOTENCY_RECORD;
            dsl.update(t.table())
                    .set(t.field("status", String.class), IdempotencyStatus.FAILED.name())
                    .set(t.field("error_code", String.class), truncate(errorCode, 64))
                    .set(t.field("error_message", String.class), truncate(errorMessage, 500))
                    .set(t.field("updated_at", LocalDateTime.class), LocalDateTime.now())
                    .where(keyCondition(key, scope))
                    .execute();
            return null;
        });
    }

    /** 非业务异常（系统错误）：删除记录，让客户端可以安全重试。 */
    public void release(String key, String scope) {
        requiresNew.execute(status -> {
            var t = SqlTables.SYS_IDEMPOTENCY_RECORD;
            dsl.deleteFrom(t.table()).where(keyCondition(key, scope)).execute();
            return null;
        });
    }

    /** 清理过期记录，由 Quartz 周期调用。 */
    public int purgeExpired() {
        Integer deleted = requiresNew.execute(status -> {
            var t = SqlTables.SYS_IDEMPOTENCY_RECORD;
            return dsl.deleteFrom(t.table())
                    .where(t.field("expires_at", LocalDateTime.class).lt(LocalDateTime.now()))
                    .execute();
        });
        return deleted == null ? 0 : deleted;
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private AcquireResult doAcquire(String key, String scope, Long userId, String fingerprint, int ttlSeconds) {
        var t = SqlTables.SYS_IDEMPOTENCY_RECORD;
        LocalDateTime now = LocalDateTime.now();
        IdempotencyRecord existing = find(key, scope);
        // 读路径兜底：清理任务未运行/失败时，已过期记录必须视为不存在并重新登记，
        // 否则过期的 SUCCESS 仍会 REPLAY 旧响应、过期的 PROCESSING 会永久返回 409。
        if (existing != null && IdempotencyRecordRules.isExpired(existing.getExpiresAt(), now)) {
            dsl.deleteFrom(t.table()).where(SqlWriters.withTenant(t,
                    t.field("id", Long.class).eq(existing.getId()))).execute();
            existing = null;
        }
        if (existing == null) {
            try {
                IdempotencyRecord record = new IdempotencyRecord();
                record.setIdempotencyKey(key);
                record.setApiScope(scope);
                record.setUserId(userId);
                record.setRequestFingerprint(fingerprint);
                record.setStatus(IdempotencyStatus.PROCESSING.name());
                record.setExpiresAt(now.plusSeconds(Math.max(ttlSeconds, 1)));
                SqlWriters.insert(dsl, t, record);
                return AcquireResult.proceed();
            } catch (DataIntegrityViolationException e) {
                // 并发抢登记：唯一索引兜底。冲突行此刻已提交，重查必然可见
                existing = find(key, scope);
                if (existing == null) {
                    // 理论上不可达；真出现时宁可拒绝也不放行，避免重复执行
                    log.warn("幂等登记冲突但重查为空，按冲突处理 key={} scope={}", key, scope);
                    return new AcquireResult(IdempotencyAction.CONFLICT, null);
                }
            }
        }

        boolean matched = Objects.equals(existing.getRequestFingerprint(), fingerprint);
        IdempotencyAction action = IdempotencyRules.decide(IdempotencyStatus.safeOf(existing.getStatus()), matched);
        if (action == IdempotencyAction.PROCEED) {
            // FAILED → PROCESSING 必须带状态 CAS：两个并发重试同时读到 FAILED 时，
            // 只有真正抢到状态跃迁的请求可以执行业务，否则会重复审批/转办/发消息。
            // 唯一索引只兜底首次 INSERT，对已存在记录的状态跃迁无约束力。
            Condition claim = SqlWriters.withTenant(t,
                    t.field("id", Long.class).eq(existing.getId())
                            .and(t.field("status", String.class).eq(IdempotencyStatus.FAILED.name())));
            int claimed = dsl.update(t.table())
                    .set(t.field("status", String.class), IdempotencyStatus.PROCESSING.name())
                    .set(t.field("response_snapshot", String.class), (String) null)
                    .set(t.field("error_code", String.class), (String) null)
                    .set(t.field("error_message", String.class), (String) null)
                    .set(t.field("expires_at", LocalDateTime.class),
                            now.plusSeconds(Math.max(ttlSeconds, 1)))
                    .set(t.field("updated_at", LocalDateTime.class), now)
                    .where(claim)
                    .execute();
            if (claimed == 0) {
                // 另一个重试者已抢占：按最新状态返回 REPLAY / CONFLICT / MISMATCH，
                // 但绝不把 CAS 失败当成 PROCEED。
                IdempotencyRecord latest = find(key, scope);
                IdempotencyAction latestAction = latest == null
                        ? IdempotencyAction.CONFLICT
                        : IdempotencyRules.decide(IdempotencyStatus.safeOf(latest.getStatus()), matched);
                if (latestAction == IdempotencyAction.PROCEED) {
                    latestAction = IdempotencyAction.CONFLICT;
                }
                return new AcquireResult(latestAction, latest == null ? null : latest.getResponseSnapshot());
            }
        }
        return new AcquireResult(action, existing.getResponseSnapshot());
    }

    private IdempotencyRecord find(String key, String scope) {
        return SqlWriters.fetchOne(dsl, SqlTables.SYS_IDEMPOTENCY_RECORD, IdempotencyRecord.class,
                keyCondition(key, scope));
    }

    private Condition keyCondition(String key, String scope) {
        var t = SqlTables.SYS_IDEMPOTENCY_RECORD;
        Condition base = DSL.and(t.field("idempotency_key", String.class).eq(key),
                t.field("api_scope", String.class).eq(scope));
        // 读写同口径：find() 经 SqlWriters.fetchOne 自带租户条件，写路径必须显式补齐，
        // 否则不同租户使用相同 key/scope 时 markSuccess/markFailed/release 会互相污染。
        return SqlWriters.withTenant(t, base);
    }

    private String toSnapshot(Object response) {
        // void 方法或返回 null 的成功响应：存 JSON null 字面量而非 Java null。
        // 否则重放路径会把「快照缺失」误判为不可重放而 409 拒绝——@Idempotent 的
        // 两处业务（WorkflowService#completeTask/#transfer）均为 void，不存 sentinel
        // 时客户端任何同 key 重试都被拦截，「幂等」退化为「拒绝重试」。
        if (response == null) {
            return "null";
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            if (json.length() > MAX_SNAPSHOT_CHARS) {
                log.warn("幂等响应快照超长，放弃存储 length={}", json.length());
                return null;
            }
            return json;
        } catch (Exception e) {
            log.warn("序列化幂等响应快照失败，放弃存储", e);
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 登记结果：动作 + 首次响应快照（仅 REPLAY 有效）。 */
    public record AcquireResult(IdempotencyAction action, String responseSnapshot) {

        public static AcquireResult proceed() {
            return new AcquireResult(IdempotencyAction.PROCEED, null);
        }
    }
}
