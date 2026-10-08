package com.acme.scaffold.common.idempotency;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 幂等记录读写。
 *
 * <p><b>关键设计：所有写操作都在 {@code REQUIRES_NEW} 独立事务中执行。</b>
 * 若与业务共用事务，业务回滚会把「已登记」的幂等记录一并抹掉，
 * 客户端重试就会变成一次真实的新执行——幂等形同虚设。
 * 独立事务的代价是：业务失败但记录已落 SUCCESS 的窗口极小（仅在提交后写回阶段失败），
 * 此时由 markFailed 覆盖状态，保证最终一致。
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
            var t = JooqTables.SYS_IDEMPOTENCY_RECORD;
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
            var t = JooqTables.SYS_IDEMPOTENCY_RECORD;
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
            var t = JooqTables.SYS_IDEMPOTENCY_RECORD;
            dsl.deleteFrom(t.table()).where(keyCondition(key, scope)).execute();
            return null;
        });
    }

    /** 清理过期记录，由 JobRunr 周期调用。 */
    public int purgeExpired() {
        Integer deleted = requiresNew.execute(status -> {
            var t = JooqTables.SYS_IDEMPOTENCY_RECORD;
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
        var t = JooqTables.SYS_IDEMPOTENCY_RECORD;
        IdempotencyRecordDO existing = find(key, scope);
        if (existing == null) {
            try {
                IdempotencyRecordDO record = new IdempotencyRecordDO();
                record.setIdempotencyKey(key);
                record.setApiScope(scope);
                record.setUserId(userId);
                record.setRequestFingerprint(fingerprint);
                record.setStatus(IdempotencyStatus.PROCESSING.name());
                record.setExpiresAt(LocalDateTime.now().plusSeconds(Math.max(ttlSeconds, 1)));
                JooqWriters.insert(dsl, t, record);
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
            // FAILED → 重新进入 PROCESSING，清空上次结果避免误重放
            dsl.update(t.table())
                    .set(t.field("status", String.class), IdempotencyStatus.PROCESSING.name())
                    .set(t.field("response_snapshot", String.class), (String) null)
                    .set(t.field("error_code", String.class), (String) null)
                    .set(t.field("error_message", String.class), (String) null)
                    .set(t.field("expires_at", LocalDateTime.class),
                            LocalDateTime.now().plusSeconds(Math.max(ttlSeconds, 1)))
                    .set(t.field("updated_at", LocalDateTime.class), LocalDateTime.now())
                    .where(t.field("id", Long.class).eq(existing.getId()))
                    .execute();
        }
        return new AcquireResult(action, existing.getResponseSnapshot());
    }

    private IdempotencyRecordDO find(String key, String scope) {
        return JooqWriters.fetchOne(dsl, JooqTables.SYS_IDEMPOTENCY_RECORD, IdempotencyRecordDO.class,
                keyCondition(key, scope));
    }

    private org.jooq.Condition keyCondition(String key, String scope) {
        var t = JooqTables.SYS_IDEMPOTENCY_RECORD;
        return DSL.and(t.field("idempotency_key", String.class).eq(key),
                t.field("api_scope", String.class).eq(scope));
    }

    private String toSnapshot(Object response) {
        if (response == null) {
            return null;
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
