package com.acme.scaffold.security.permission;

import com.acme.scaffold.jooq.JooqTables;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DataScopeConditions} 单元测试：把 {@link DataScopeContext} 翻译成 jOOQ 条件。
 * 不依赖数据库：用 {@code DSL.using(SQLDialect.MYSQL)} 仅做 SQL 渲染断言。
 */
class DataScopeConditionsTest {

    private final DSLContext ctx = DSL.using(SQLDialect.MYSQL);

    @AfterEach
    void clear() {
        DataScopeContext.clear();
    }

    private String renderUser(Condition c) {
        return ctx.renderInlined(ctx.selectOne().from(JooqTables.SYS_USER.table()).where(c))
                .toString().toLowerCase().replace("`", "");
    }

    private String renderOrg(Condition c) {
        return ctx.renderInlined(ctx.selectOne().from(JooqTables.SYS_ORG.table()).where(c))
                .toString().toLowerCase().replace("`", "");
    }

    private String trueUserSql() {
        return ctx.renderInlined(ctx.selectOne().from(JooqTables.SYS_USER.table()).where(DSL.trueCondition()))
                .toString().toLowerCase().replace("`", "");
    }

    private String trueOrgSql() {
        return ctx.renderInlined(ctx.selectOne().from(JooqTables.SYS_ORG.table()).where(DSL.trueCondition()))
                .toString().toLowerCase().replace("`", "");
    }

    @Test
    void noContextYieldsUnrestricted() {
        Condition c = DataScopeConditions.of(JooqTables.SYS_USER, "org_id", "id");
        assertEquals(trueUserSql(), renderUser(c));
    }

    @Test
    void allYieldsUnrestricted() {
        DataScopeContext.set(new DataScopeContext.Scope(1L, DataScopeResult.all()));
        Condition c = DataScopeConditions.of(JooqTables.SYS_USER, "org_id", "id");
        assertEquals(trueUserSql(), renderUser(c));
    }

    @Test
    void selfWithUserColumn() {
        DataScopeContext.set(new DataScopeContext.Scope(5L, DataScopeResult.self()));
        Condition c = DataScopeConditions.of(JooqTables.SYS_USER, "org_id", "id");
        String sql = renderUser(c);
        assertTrue(sql.contains("id"), sql);
        assertTrue(sql.contains("5"), sql);
        assertFalse(sql.contains("org_id"), sql);
    }

    @Test
    void selfWithoutUserColumnDegradesToOrgSubquery() {
        DataScopeContext.set(new DataScopeContext.Scope(5L, DataScopeResult.self()));
        Condition c = DataScopeConditions.of(JooqTables.SYS_ORG, "id", null);
        String sql = renderOrg(c);
        assertTrue(sql.contains("id in (select"), sql);
        assertTrue(sql.contains("sys_user"), sql);
        assertTrue(sql.contains("= 5"), sql);
    }

    @Test
    void selfWithNullUserYieldsUnrestricted() {
        DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.self()));
        Condition c = DataScopeConditions.of(JooqTables.SYS_USER, "org_id", "id");
        assertEquals(trueUserSql(), renderUser(c));
    }

    @Test
    void customOrgIdsInClause() {
        DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.ofOrgs(Set.of(1L, 2L))));
        Condition c = DataScopeConditions.of(JooqTables.SYS_USER, "org_id", "id");
        String sql = renderUser(c);
        assertTrue(sql.contains("org_id"), sql);
        assertTrue(sql.contains("in ("), sql);
        assertTrue(sql.contains("1") && sql.contains("2"), sql);
    }

    @Test
    void customWithSingleOrgInClause() {
        DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.ofOrgs(Set.of(7L))));
        Condition c = DataScopeConditions.of(JooqTables.SYS_USER, "org_id", "id");
        String sql = renderUser(c);
        assertTrue(sql.contains("org_id in (7)") || sql.contains("org_id in(7)"), sql);
    }

    @Test
    void missingOrgColumnYieldsUnrestricted() {
        DataScopeContext.set(new DataScopeContext.Scope(null, DataScopeResult.ofOrgs(Set.of(1L))));
        // SYS_ORG 不存在 nonexistent_col -> 不加条件（仅告警）
        Condition c = DataScopeConditions.of(JooqTables.SYS_ORG, "nonexistent_col", null);
        assertEquals(trueOrgSql(), renderOrg(c));
    }
}
