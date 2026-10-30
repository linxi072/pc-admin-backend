package com.acme.scaffold.system.dashboard;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.system.entity.SysOnlineSessionDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

/**
 * 基于手写 jOOQ DSL 的系统统计适配器（M27）。复用 {@code JooqTables} 表引用与
 * {@code DSLContext}，不引 ORM；任一查询失败静默回落（计数类回落 0、趋势类回落空序列），
 * 保证看板始终可用。
 *
 * <p>看板为平台级全局统计，经控制器 {@code @TenantIgnorable} 跨租户聚合；
 * 因此这里直接对全表计数，不附加 tenant_id 过滤。</p>
 * <p>时间序列按 MySQL {@code DATE(expr)} 按天分组；缺数据的日期由 {@link DashboardRules#fillDailyGaps} 补齐。</p>
 */
@Component
@RequiredArgsConstructor
public class JooqSystemStatsAdapter implements SystemStatsPort {

    private static final JooqTables.TableRef OS = JooqTables.toTableRef(Tables.SYS_ONLINE_SESSION);

    private final DSLContext dsl;

    @Override
    public long countTenants() {
        return safe(() -> dsl.fetchCount(JooqTables.SYS_TENANT.table()));
    }

    @Override
    public long countUsers() {
        return safe(() -> dsl.fetchCount(JooqTables.SYS_USER.table(),
                JooqWriters.notDeleted(JooqTables.SYS_USER)));
    }

    @Override
    public long countRoles() {
        return safe(() -> dsl.fetchCount(JooqTables.SYS_ROLE.table()));
    }

    @Override
    public long countMenus() {
        return safe(() -> dsl.fetchCount(JooqTables.SYS_MENU.table()));
    }

    @Override
    public long countOnlineSessions() {
        Condition online = OS.field("status", String.class)
                .eq(SysOnlineSessionDO.STATUS_ONLINE);
        return safe(() -> dsl.fetchCount(OS.table(), online));
    }

    @Override
    public long countIdempotencyRecords() {
        return safe(() -> dsl.fetchCount(Tables.SYS_IDEMPOTENCY_RECORD));
    }

    @Override
    public long countOperationLogs() {
        return safe(() -> dsl.fetchCount(Tables.SYS_OPERATION_LOG));
    }

    @Override
    public long countLoginLogsToday() {
        // 注意：登录日志时间列为 occurred_at（非 login_at），与 SYS_LOGIN_LOG 表定义严格一致
        Timestamp startOfDay = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        Condition today = Tables.SYS_LOGIN_LOG.OCCURRED_AT.cast(Timestamp.class).ge(startOfDay);
        return safe(() -> dsl.fetchCount(Tables.SYS_LOGIN_LOG, today));
    }

    @Override
    public List<TrendPoint> dailyNewUsers(int days) {
        return safeList(() -> {
            LocalDate start = LocalDate.now().minusDays(Math.max(1, days) - 1L);
            Field<Timestamp> ts = JooqTables.SYS_USER.field("created_at", Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(JooqTables.SYS_USER.table())
                    .where(JooqWriters.notDeleted(JooqTables.SYS_USER)
                            .and(ts.ge(Timestamp.valueOf(start.atStartOfDay()))))
                    .groupBy(day)
                    .orderBy(day)
                    .fetch()
                    .map(r -> new TrendPoint(r.value1(), toLong(r.value2())));
        });
    }

    @Override
    public List<TrendPoint> dailyNewTenants(int days) {
        return safeList(() -> {
            LocalDate start = LocalDate.now().minusDays(Math.max(1, days) - 1L);
            Field<Timestamp> ts = JooqTables.SYS_TENANT.field("created_at", Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(JooqTables.SYS_TENANT.table())
                    .where(JooqWriters.notDeleted(JooqTables.SYS_TENANT)
                            .and(ts.ge(Timestamp.valueOf(start.atStartOfDay()))))
                    .groupBy(day)
                    .orderBy(day)
                    .fetch()
                    .map(r -> new TrendPoint(r.value1(), toLong(r.value2())));
        });
    }

    @Override
    public List<TrendPoint> dailyActiveTenants(int days) {
        return safeList(() -> {
            LocalDate start = LocalDate.now().minusDays(Math.max(1, days) - 1L);
            Field<Timestamp> ts = Tables.SYS_LOGIN_LOG.OCCURRED_AT.cast(Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            Field<Long> tenantId = Tables.SYS_LOGIN_LOG.TENANT_ID;
            return dsl.select(day, DSL.countDistinct(tenantId))
                    .from(Tables.SYS_LOGIN_LOG)
                    .where(ts.ge(Timestamp.valueOf(start.atStartOfDay())))
                    .groupBy(day)
                    .orderBy(day)
                    .fetch()
                    .map(r -> new TrendPoint(r.value1(), toLong(r.value2())));
        });
    }

    @Override
    public List<TrendPoint> dailyLoginLogs(int days) {
        return safeList(() -> {
            LocalDate start = LocalDate.now().minusDays(Math.max(1, days) - 1L);
            Field<Timestamp> ts = Tables.SYS_LOGIN_LOG.OCCURRED_AT.cast(Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(Tables.SYS_LOGIN_LOG)
                    .where(ts.ge(Timestamp.valueOf(start.atStartOfDay())))
                    .groupBy(day)
                    .orderBy(day)
                    .fetch()
                    .map(r -> new TrendPoint(r.value1(), toLong(r.value2())));
        });
    }

    @Override
    public List<TrendPoint> dailyOperationLogs(int days) {
        return safeList(() -> {
            LocalDate start = LocalDate.now().minusDays(Math.max(1, days) - 1L);
            Field<Timestamp> ts = Tables.SYS_OPERATION_LOG.OCCURRED_AT.cast(Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(Tables.SYS_OPERATION_LOG)
                    .where(ts.ge(Timestamp.valueOf(start.atStartOfDay())))
                    .groupBy(day)
                    .orderBy(day)
                    .fetch()
                    .map(r -> new TrendPoint(r.value1(), toLong(r.value2())));
        });
    }

    @Override
    public List<TrendPoint> dailyNotifications(int days) {
        return safeList(() -> {
            LocalDate start = LocalDate.now().minusDays(Math.max(1, days) - 1L);
            Field<Timestamp> ts = Tables.SYS_NOTIFICATION_RECORD.SENT_AT.cast(Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(Tables.SYS_NOTIFICATION_RECORD)
                    .where(ts.ge(Timestamp.valueOf(start.atStartOfDay())))
                    .groupBy(day)
                    .orderBy(day)
                    .fetch()
                    .map(r -> new TrendPoint(r.value1(), toLong(r.value2())));
        });
    }

    /** 按天截断的时间字段：MySQL {@code DATE(expr)}。 */
    private static Field<LocalDate> dateField(Field<Timestamp> ts) {
        return DSL.field("DATE({0})", LocalDate.class, ts);
    }

    /** 统计查询统一兜底：异常即回落 0（看板不因单一统计失败而整体报错）。 */
    private static long safe(CountCall call) {
        try {
            return Math.max(0L, call.run());
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 趋势查询统一兜底：异常即回落空序列（缺口由规则补齐为 0）。 */
    private static <T> List<T> safeList(Supplier<List<T>> call) {
        try {
            List<T> result = call.get();
            return result == null ? List.of() : result;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 计数结果统一转非负 long（null 或负数回落 0）。 */
    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number n) {
            return Math.max(0L, n.longValue());
        }
        return 0L;
    }

    @FunctionalInterface
    private interface CountCall {
        long run();
    }
}
