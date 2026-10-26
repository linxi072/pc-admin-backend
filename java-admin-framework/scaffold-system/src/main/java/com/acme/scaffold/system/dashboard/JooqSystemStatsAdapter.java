package com.acme.scaffold.system.dashboard;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.entity.SysOnlineSessionDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 基于手写 jOOQ DSL 的系统统计适配器（M27）。复用 {@code JooqTables} 表引用与
 * {@code DSLContext#fetchCount}，不引 ORM；任一查询失败静默回落 0，保证看板始终可用。
 *
 * <p>看板为平台级全局统计，经控制器 {@code @TenantIgnorable} 跨租户聚合；
 * 因此这里直接对全表计数，不附加 tenant_id 过滤。</p>
 */
@Component
@RequiredArgsConstructor
public class JooqSystemStatsAdapter implements SystemStatsPort {

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
        Condition online = JooqTables.SYS_ONLINE_SESSION.field("status", String.class)
                .eq(SysOnlineSessionDO.STATUS_ONLINE);
        return safe(() -> dsl.fetchCount(JooqTables.SYS_ONLINE_SESSION.table(), online));
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
        Timestamp startOfDay = Timestamp.valueOf(LocalDate.now().atStartOfDay());
        Condition today = JooqTables.SYS_LOGIN_LOG.field("login_at", Timestamp.class).ge(startOfDay);
        return safe(() -> dsl.fetchCount(JooqTables.SYS_LOGIN_LOG.table(), today));
    }

    /** 统计查询统一兜底：异常即回落 0（看板不因单一统计失败而整体报错）。 */
    private static long safe(CountCall call) {
        try {
            return Math.max(0L, call.run());
        } catch (Exception e) {
            return 0L;
        }
    }

    @FunctionalInterface
    private interface CountCall {
        long run();
    }
}
