package com.acme.scaffold.system.dashboard;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.entity.SysOnlineSessionDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.impl.DSL;

import com.acme.scaffold.system.dashboard.dto.HourlyPoint;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final JooqTables.TableRef USER = JooqTables.SYS_USER;

    private static final JooqTables.TableRef OS = JooqTables.SYS_ONLINE_SESSION;
    private static final JooqTables.TableRef TN = JooqTables.SYS_TENANT;

    private static final JooqTables.TableRef ROLE = JooqTables.SYS_ROLE;

    private final DSLContext dsl;

    @Override
    public long countTenants() {
        return safe(() -> dsl.fetchCount(TN.table()));
    }

    @Override
    public long countUsers() {
        return safe(() -> dsl.fetchCount(USER.table(),
                JooqWriters.notDeleted(USER)));
    }

    @Override
    public long countRoles() {
        return safe(() -> dsl.fetchCount(ROLE.table()));
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
        return safe(() -> dsl.fetchCount(JooqTables.SYS_IDEMPOTENCY_RECORD.table()));
    }

    @Override
    public long countOperationLogs() {
        return safe(() -> dsl.fetchCount(JooqTables.SYS_OPERATION_LOG.table()));
    }

    @Override
    public long countLoginLogsToday() {
        // 注意：登录日志时间列为 occurred_at（非 login_at），与 SYS_LOGIN_LOG 表定义严格一致
        Timestamp startOfDay = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        Condition today = JooqTables.SYS_LOGIN_LOG.field("occurred_at", java.time.LocalDateTime.class).cast(Timestamp.class).ge(startOfDay);
        return safe(() -> dsl.fetchCount(JooqTables.SYS_LOGIN_LOG.table(), today));
    }

    @Override
    public List<TrendPoint> dailyNewUsers(int days) {
        return safeList(() -> {
            LocalDate start = LocalDate.now().minusDays(Math.max(1, days) - 1L);
            Field<Timestamp> ts = USER.field("created_at", Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(USER.table())
                    .where(JooqWriters.notDeleted(USER)
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
            Field<Timestamp> ts = TN.field("created_at", Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(TN.table())
                    .where(JooqWriters.notDeleted(TN)
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
            Field<Timestamp> ts = JooqTables.SYS_LOGIN_LOG.field("occurred_at", java.time.LocalDateTime.class).cast(Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            Field<Long> tenantId = JooqTables.SYS_LOGIN_LOG.field("tenant_id", Long.class);
            return dsl.select(day, DSL.countDistinct(tenantId))
                    .from(JooqTables.SYS_LOGIN_LOG.table())
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
            Field<Timestamp> ts = JooqTables.SYS_LOGIN_LOG.field("occurred_at", java.time.LocalDateTime.class).cast(Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(JooqTables.SYS_LOGIN_LOG.table())
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
            Field<Timestamp> ts = JooqTables.SYS_OPERATION_LOG.field("occurred_at", java.time.LocalDateTime.class).cast(Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(JooqTables.SYS_OPERATION_LOG.table())
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
            Field<Timestamp> ts = JooqTables.SYS_NOTIFICATION_RECORD.field("sent_at", java.time.LocalDateTime.class).cast(Timestamp.class);
            Field<LocalDate> day = dateField(ts);
            return dsl.select(day, DSL.count())
                    .from(JooqTables.SYS_NOTIFICATION_RECORD.table())
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

    @Override
    public List<HourlyPoint> hourlyMetric(DashboardDrillMetric metric, LocalDate date) {
        return safeList(() -> {
            LocalDate d = (date == null) ? LocalDate.now() : date;
            Timestamp dayStart = Timestamp.valueOf(d.atStartOfDay());
            Timestamp dayEnd = Timestamp.valueOf(d.atTime(23, 59, 59, 999_000_000));
            return switch (metric) {
                case NEW_USERS -> hourly(USER, USER.field("created_at", Timestamp.class),
                        JooqWriters.notDeleted(USER), dayStart, dayEnd);
                case NEW_TENANTS -> hourly(TN, TN.field("created_at", Timestamp.class),
                        JooqWriters.notDeleted(TN), dayStart, dayEnd);
                case ACTIVE_TENANTS -> hourlyDistinct(JooqTables.SYS_LOGIN_LOG,
                        tscol(JooqTables.SYS_LOGIN_LOG, "occurred_at"),
                        JooqTables.SYS_LOGIN_LOG.field("tenant_id", Long.class), dayStart, dayEnd);
                case LOGIN_LOGS -> hourly(JooqTables.SYS_LOGIN_LOG,
                        tscol(JooqTables.SYS_LOGIN_LOG, "occurred_at"), null, dayStart, dayEnd);
                case OPERATION_LOGS -> hourly(JooqTables.SYS_OPERATION_LOG,
                        tscol(JooqTables.SYS_OPERATION_LOG, "occurred_at"), null, dayStart, dayEnd);
                case NOTIFICATIONS -> hourly(JooqTables.SYS_NOTIFICATION_RECORD,
                        tscol(JooqTables.SYS_NOTIFICATION_RECORD, "sent_at"), null, dayStart, dayEnd);
            };
        });
    }

    /** 指定日期逐小时计数（缺小时补零，0–23）。 */
    private List<HourlyPoint> hourly(JooqTables.TableRef t, Field<Timestamp> ts, Condition extra,
                                     Timestamp dayStart, Timestamp dayEnd) {
        Field<Integer> hour = DSL.field("HOUR({0})", Integer.class, ts);
        Condition cond = ts.ge(dayStart).and(ts.le(dayEnd));
        if (extra != null) {
            cond = cond.and(extra);
        }
        Result<Record2<Integer, Integer>> rs = dsl.select(hour, DSL.count())
                .from(t.table()).where(cond).groupBy(hour).fetch();
        Map<Integer, Long> map = new HashMap<>();
        for (Record2<Integer, Integer> r : rs) {
            map.put(r.value1(), toLong(r.value2()));
        }
        List<HourlyPoint> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            out.add(new HourlyPoint(h, map.getOrDefault(h, 0L)));
        }
        return out;
    }

    /** 指定日期逐小时去重计数（如活跃租户数）。 */
    private List<HourlyPoint> hourlyDistinct(JooqTables.TableRef t, Field<Timestamp> ts, Field<Long> distinctField,
                                            Timestamp dayStart, Timestamp dayEnd) {
        Field<Integer> hour = DSL.field("HOUR({0})", Integer.class, ts);
        Condition cond = ts.ge(dayStart).and(ts.le(dayEnd));
        Result<Record2<Integer, Integer>> rs = dsl.select(hour, DSL.countDistinct(distinctField))
                .from(t.table()).where(cond).groupBy(hour).fetch();
        Map<Integer, Long> map = new HashMap<>();
        for (Record2<Integer, Integer> r : rs) {
            map.put(r.value1(), toLong(r.value2()));
        }
        List<HourlyPoint> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            out.add(new HourlyPoint(h, map.getOrDefault(h, 0L)));
        }
        return out;
    }

    /** 将 LocalDateTime 列安全转为 Timestamp 类型字段（与每日趋势口径一致）。 */
    private static Field<Timestamp> tscol(JooqTables.TableRef t, String col) {
        return t.field(col, java.time.LocalDateTime.class).cast(Timestamp.class);
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
