package com.acme.scaffold.common.idempotency.service;

import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.common.idempotency.IdempotencyService;
import com.acme.scaffold.common.idempotency.IdempotencyStatus;
import com.acme.scaffold.common.idempotency.domain.IdempotencyRecordDO;
import com.acme.scaffold.common.idempotency.dto.IdempotencyRecordView;
import com.acme.scaffold.common.idempotency.dto.IdempotencyStatsView;
import com.acme.scaffold.common.idempotency.rules.IdempotencyRecordRules;
import com.acme.scaffold.jooq.dsl.JooqSorts;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 幂等记录运维查询服务（M2 幂等模块的管理侧面）。
 *
 * <p><b>定位</b>：记录由 {@link IdempotencyService}（acquire / markSuccess /
 * markFailed / release）在请求链路上写入，本服务只读查询 + 委托清理，
 * <b>不提供任何单条删除</b>——删除一条仍处 PROCESSING 的记录，会让同 key 的
 * 在途请求失去「第二道防线」而重复执行业务，直接破坏 M2 的核心保证。
 * 唯一可执行的清理是「删除已过期记录」（与 {@code IdempotencyCleanupJob}
 * 完全同口径）：过期即超出幂等窗口，同 key 再现本就是全新业务操作。</p>
 *
 * <p><b>检索口径</b>：{@code response_snapshot} 不参与关键字检索也不在视图中暴露
 * （体积大且含业务响应体）；关键字覆盖 key / scope / error_code / error_message
 * 四个运维相关列。时间窗默认收敛到最近 7 天，与登录日志 / 短信记录页一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyRecordService {

    /** 幂等记录可排序字段白名单：客户端字段名 → 数据库列名（白名单外一律忽略）。 */
    private static final Map<String, String> IDEMPOTENCY_RECORD_SORT_FIELDS = JooqSorts.whitelist(
            "id", "id",
            "idempotencyKey", "idempotency_key",
            "apiScope", "api_scope",
            "userId", "user_id",
            "status", "status",
            "expiresAt", "expires_at",
            "createdAt", "created_at",
            "updatedAt", "updated_at");

    private final DSLContext dsl;
    private final IdempotencyService idempotencyService;

    /**
     * 分页查询幂等记录。
     *
     * @param page          页码（从 1 开始，越界由 {@link PageQuery} 钳制）
     * @param size          每页条数（上限 100）
     * @param status        状态精确筛选（PROCESSING / SUCCESS / FAILED），归一后非法值忽略
     * @param apiScope      接口域精确筛选，归一后空值忽略
     * @param userId        用户 ID 筛选（字符串承接，非数字忽略，避免类型转换失败穿透 500）
     * @param keyword       关键字模糊筛选（key / scope / error_code / error_message），空不过滤
     * @param startTime     起始时间（含，按 created_at），为空默认 7 天前
     * @param endTime       结束时间（含），为空默认当前
     * @param sortField     排序字段（服务端白名单映射，白名单外忽略）
     * @param sortDirection 排序方向：ASC / DESC，非法值抛 {@code VALIDATION_ERROR} → 400
     */
    public PageResult<IdempotencyRecordView> page(
            int page, int size, String status, String apiScope, String userId, String keyword,
            LocalDateTime startTime, LocalDateTime endTime, String sortField, String sortDirection) {
        LocalDateTime now = LocalDateTime.now();
        Condition cond = buildCondition(status, apiScope, userId, keyword, startTime, endTime, now);
        PageQuery query = PageQuery.of(page, size, sortField, sortDirection);
        var table = JooqTables.SYS_IDEMPOTENCY_RECORD;
        PageResult<IdempotencyRecordDO> raw = JooqWriters.page(dsl, table, IdempotencyRecordDO.class,
                cond, query, JooqSorts.resolve(table, query, IDEMPOTENCY_RECORD_SORT_FIELDS,
                        table.field("created_at", LocalDateTime.class).desc(),
                        table.field("id", Long.class).desc()));
        List<IdempotencyRecordView> views = new ArrayList<>(raw.records().size());
        for (IdempotencyRecordDO record : raw.records()) {
            views.add(IdempotencyRecordView.of(record, now));
        }
        return PageResult.of(raw.page(), raw.size(), raw.total(), views);
    }

    /**
     * 统计摘要（与 {@link #page} 相同筛选口径）。
     *
     * @return 总数 / 三态分布 / 已过期待清理数 / PROCESSING 占比 / 最近登记时间
     */
    public IdempotencyStatsView summary(
            String status, String apiScope, String userId, String keyword,
            LocalDateTime startTime, LocalDateTime endTime) {
        LocalDateTime now = LocalDateTime.now();
        Condition cond = buildCondition(status, apiScope, userId, keyword, startTime, endTime, now);
        var table = JooqTables.SYS_IDEMPOTENCY_RECORD;
        long total = JooqWriters.count(dsl, table, cond);
        long processing = countByStatus(cond, IdempotencyStatus.PROCESSING);
        long success = countByStatus(cond, IdempotencyStatus.SUCCESS);
        long failed = countByStatus(cond, IdempotencyStatus.FAILED);
        long expiredPending = JooqWriters.count(dsl, table, DSL.and(cond,
                table.field("expires_at", LocalDateTime.class).lt(now)));
        LocalDateTime lastCreatedAt = dsl.select(table.field("created_at", LocalDateTime.class).max())
                .from(table.table()).where(cond).fetchOne(0, LocalDateTime.class);
        return buildStats(total, processing, success, failed, expiredPending, lastCreatedAt);
    }

    /**
     * 手动清理过期幂等记录（与 {@code IdempotencyCleanupJob} 周期任务同口径同实现）。
     *
     * @return 实际删除条数（0 表示无过期记录或清理任务刚跑过）
     */
    public int purgeExpired() {
        return idempotencyService.purgeExpired();
    }

    // ------------------------------------------------------------------
    // 内部实现
    // ------------------------------------------------------------------

    private long countByStatus(Condition base, IdempotencyStatus status) {
        return JooqWriters.count(dsl, JooqTables.SYS_IDEMPOTENCY_RECORD,
                DSL.and(base, JooqTables.SYS_IDEMPOTENCY_RECORD.field("status", String.class)
                        .eq(status.name())));
    }

    /**
     * 摘要组装（纯函数，与 DB 解耦便于离线单测）：PROCESSING 占比保留 4 位小数，
     * 无记录时占比为 0（避免除零与 NaN 进入 JSON）。
     */
    static IdempotencyStatsView buildStats(long total, long processing, long success, long failed,
                                           long expiredPending, LocalDateTime lastCreatedAt) {
        double share = total <= 0 ? 0.0 : Math.round((double) processing / total * 10000d) / 10000d;
        return new IdempotencyStatsView(total, processing, success, failed, expiredPending,
                share, lastCreatedAt);
    }

    /** 统一筛选条件构造：时间窗收敛 + 各筛选值经规则类归一（非法值忽略）。 */
    private Condition buildCondition(String status, String apiScope, String userId, String keyword,
                                     LocalDateTime startTime, LocalDateTime endTime, LocalDateTime now) {
        IdempotencyRecordRules.Window window =
                IdempotencyRecordRules.clampWindow(startTime, endTime, now);
        var table = JooqTables.SYS_IDEMPOTENCY_RECORD;
        List<Condition> conds = new ArrayList<>();
        conds.add(DSL.and(
                table.field("created_at", LocalDateTime.class).ge(window.begin()),
                table.field("created_at", LocalDateTime.class).le(window.end())));
        String st = IdempotencyRecordRules.normalizeStatus(status);
        if (st != null) {
            conds.add(table.field("status", String.class).eq(st));
        }
        String scope = IdempotencyRecordRules.normalizeScope(apiScope);
        if (scope != null) {
            conds.add(table.field("api_scope", String.class).eq(scope));
        }
        Long uid = IdempotencyRecordRules.normalizeUserId(userId);
        if (uid != null) {
            conds.add(table.field("user_id", Long.class).eq(uid));
        }
        String kw = IdempotencyRecordRules.wrapKeyword(keyword);
        if (kw != null) {
            conds.add(DSL.or(
                    table.field("idempotency_key", String.class).like(kw),
                    table.field("api_scope", String.class).like(kw),
                    table.field("error_code", String.class).like(kw),
                    table.field("error_message", String.class).like(kw)));
        }
        return DSL.and(conds);
    }
}
