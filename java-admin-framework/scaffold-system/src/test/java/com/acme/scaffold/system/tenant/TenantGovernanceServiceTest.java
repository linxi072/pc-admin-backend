package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.jooq.dsl.TenantTables;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqTables.TableRef;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.SQLDialect;
import org.jooq.conf.ParamType;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TenantGovernanceService 自测（Docker-free，MockConnection 捕获渲染后的 SQL）。
 *
 * <p>验证跨租户治理动作（总览 / 归位 / 迁移）仅在业务表集合上发起 SQL，绝不动平台维表，
 * 且正确在旁路作用域下执行（不追加 tenant_id 过滤）。</p>
 */
class TenantGovernanceServiceTest {

    private static final long CURRENT_TENANT = 1L;

    private final List<Executed> executed = new java.util.ArrayList<>();

    private static final class Executed {
        final String sql;
        final Object[] bindings;

        Executed(String sql, Object[] bindings) {
            this.sql = sql != null ? sql : "";
            this.bindings = bindings != null ? bindings : new Object[0];
        }

        boolean isUpdate() {
            // 生成层 SELECT 显式投影 updated_at 等列名含 "update" 子串，须按语句前缀判断
            return sql.toLowerCase().startsWith("update");
        }

        boolean isSelectFromTenant() {
            return sql.toLowerCase().contains("sys_tenant");
        }

        boolean isCount() {
            String s = sql.toLowerCase();
            return s.contains("select") && s.contains("count");
        }

        boolean contains(String frag) {
            return sql.toLowerCase().contains(frag.toLowerCase());
        }

        boolean binds(Object v) {
            for (Object b : bindings) {
                if (Objects.equals(b, v)) {
                    return true;
                }
            }
            return false;
        }
    }

    private Connection conn;
    private DSLContext dsl;
    private TenantGovernanceService service;

    /** 业务表中含 tenant_id 列的数量（backfill/reassign 仅对这些表发起 UPDATE）。 */
    private long businessTablesWithTenantId() {
        return TenantTables.businessTableRefs().stream().filter(ref -> ref.has("tenant_id")).count();
    }

    @BeforeEach
    void init() {
        MockDataProvider provider = (MockExecuteContext ctx) -> {
            String sql = ctx.sql() == null ? "" : ctx.sql();
            Object[] bindings = ctx.bindings();
            executed.add(new Executed(sql, bindings));
            String lower = sql.toLowerCase();
            // 注意：生成层 selectFrom 显式投影 updated_by/updated_at 等列名包含 "update" 子串，
            // 故 UPDATE 语句须按前缀判断，否则 SELECT 会被误判为 UPDATE 返回空结果。
            if (lower.startsWith("update")) {
                return new MockResult[]{new MockResult(1)};
            }
            if (lower.contains("sys_tenant")) {
                // 手写 DSL 下 SYS_TENANT 的 table() 为无列元数据的纯表名，无法用 newRecord(table) 构造带列的
                // Record；故显式声明 16 个字段构造 Result/Record（列序/类型与手写表定义一致），填入非空哨兵值；
                // 仅 tenant_name 用于断言。
                LocalDateTime now = LocalDateTime.now();
                var dslMock = DSL.using(conn);
                Field<?>[] tenantCols = {
                        JooqTables.SYS_TENANT.field("id", Long.class),
                        JooqTables.SYS_TENANT.field("tenant_id", Long.class),
                        JooqTables.SYS_TENANT.field("tenant_code", String.class),
                        JooqTables.SYS_TENANT.field("tenant_name", String.class),
                        JooqTables.SYS_TENANT.field("contact_name", String.class),
                        JooqTables.SYS_TENANT.field("contact_phone", String.class),
                        JooqTables.SYS_TENANT.field("contact_email", String.class),
                        JooqTables.SYS_TENANT.field("status", String.class),
                        JooqTables.SYS_TENANT.field("expire_at", java.time.LocalDateTime.class),
                        JooqTables.SYS_TENANT.field("remark", String.class),
                        JooqTables.SYS_TENANT.field("version", Long.class),
                        JooqTables.SYS_TENANT.field("deleted", Byte.class),
                        JooqTables.SYS_TENANT.field("created_by", String.class),
                        JooqTables.SYS_TENANT.field("created_at", java.time.LocalDateTime.class),
                        JooqTables.SYS_TENANT.field("updated_by", String.class),
                        JooqTables.SYS_TENANT.field("updated_at", java.time.LocalDateTime.class)
                };
                var res = dslMock.newResult(tenantCols);
                var t = dslMock.newRecord(tenantCols);
                t.set(JooqTables.SYS_TENANT.field("id", Long.class), 1L);
                t.set(JooqTables.SYS_TENANT.field("tenant_id", Long.class), 1L);
                t.set(JooqTables.SYS_TENANT.field("tenant_code", String.class), "T1");
                t.set(JooqTables.SYS_TENANT.field("tenant_name", String.class), "T1");
                t.set(JooqTables.SYS_TENANT.field("contact_name", String.class), "c");
                t.set(JooqTables.SYS_TENANT.field("contact_phone", String.class), "p");
                t.set(JooqTables.SYS_TENANT.field("contact_email", String.class), "e");
                t.set(JooqTables.SYS_TENANT.field("status", String.class), "ACTIVE");
                t.set(JooqTables.SYS_TENANT.field("expire_at", java.time.LocalDateTime.class), now);
                t.set(JooqTables.SYS_TENANT.field("remark", String.class), "r");
                t.set(JooqTables.SYS_TENANT.field("version", Long.class), 1L);
                t.set(JooqTables.SYS_TENANT.field("deleted", Byte.class), (byte) 0);
                t.set(JooqTables.SYS_TENANT.field("created_by", String.class), "sys");
                t.set(JooqTables.SYS_TENANT.field("created_at", java.time.LocalDateTime.class), now);
                t.set(JooqTables.SYS_TENANT.field("updated_by", String.class), "sys");
                t.set(JooqTables.SYS_TENANT.field("updated_at", java.time.LocalDateTime.class), now);
                res.add(t);
                return new MockResult[]{new MockResult(1, res)};
            }
            if (lower.contains("select")) {
                Record c = DSL.using(conn).newRecord(DSL.field("cnt", Integer.class));
                c.set(DSL.field("cnt", Integer.class), 0);
                return new MockResult[]{new MockResult(c)};
            }
            return new MockResult[]{new MockResult(0)};
        };
        conn = new MockConnection(provider);
        dsl = DSL.using(conn, SQLDialect.MYSQL, new Settings().withParamType(ParamType.INDEXED));
        service = new TenantGovernanceService(dsl);
    }

    @AfterEach
    void reset() {
        TenantContext.clear();
        executed.clear();
    }

    @Test
    @DisplayName("dataOverview：对每个业务表发起计数查询，并返回每个租户的总览")
    void dataOverviewIssuesPerTableCounts() {
        TenantContext.setTenantId(CURRENT_TENANT);
        var overview = TenantContext.runWithBypass(() -> service.dataOverview());

        // 仅 1 条来自 sys_tenant 的租户列表查询
        long tenantSelects = executed.stream().filter(Executed::isSelectFromTenant).count();
        assertEquals(1, tenantSelects, "应仅查询一次租户列表");

        // 每个含 tenant_id 的业务表各发起一次计数
        long countSelects = executed.stream().filter(Executed::isCount).count();
        assertEquals(businessTablesWithTenantId(), countSelects, "计数查询数应与含 tenant_id 的业务表数一致");

        // 返回 1 个租户的总览，且明细条数 == 含 tenant_id 的业务表数
        assertEquals(1, overview.size());
        assertEquals("T1", overview.get(0).tenantName());
        assertEquals(businessTablesWithTenantId(), overview.get(0).tableCounts().size());
    }

    @Test
    @DisplayName("backfill：对全部含 tenant_id 的业务表发起 UPDATE，且写入目标租户")
    void backfillUpdatesAllBusinessTables() {
        TenantContext.setTenantId(CURRENT_TENANT);
        long affected = TenantContext.runWithBypass(() -> service.backfill(5L));

        List<Executed> updates = executed.stream().filter(Executed::isUpdate).collect(Collectors.toList());
        assertEquals(businessTablesWithTenantId(), updates.size(), "UPDATE 数应与含 tenant_id 的业务表数一致");
        for (Executed u : updates) {
            assertTrue(u.contains("tenant_id"), "backfill 的 UPDATE 必须操作 tenant_id 列");
            assertTrue(u.binds(5L), "backfill 应将 tenant_id 绑定为目标租户 5");
        }
        // 断言未误改平台维表
        for (String p : TenantTables.PLATFORM_TABLES) {
            assertFalse(updates.stream().anyMatch(u -> u.contains("update " + p)),
                    "平台维表不应被 backfill 修改: " + p);
        }
        assertEquals(businessTablesWithTenantId(), (long) affected);
    }

    @Test
    @DisplayName("reassign：按指定表集合迁移，仅命中限定表")
    void reassignWithTableFilter() {
        TenantContext.setTenantId(CURRENT_TENANT);
        TenantContext.runWithBypass(() -> service.reassign(1L, 2L, List.of("sys_user")));

        List<Executed> updates = executed.stream().filter(Executed::isUpdate).collect(Collectors.toList());
        assertEquals(1, updates.size(), "仅限 sys_user 时应只有 1 条 UPDATE");
        assertTrue(updates.get(0).contains("sys_user"));
        assertTrue(updates.get(0).binds(2L), "reassign 应将 tenant_id 绑定为目标租户 2");
        assertTrue(updates.get(0).binds(1L), "reassign 应按源租户 1 过滤");
    }
}
