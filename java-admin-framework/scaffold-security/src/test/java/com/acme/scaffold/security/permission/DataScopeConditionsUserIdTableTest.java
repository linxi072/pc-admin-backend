package com.acme.scaffold.security.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 数据权限条件在带 user_id 但无机构列的表上的过滤语义。 */
class DataScopeConditionsUserIdTableTest {

    @AfterEach
    void tearDown() {
        DataScopeContext.clear();
    }

    @Test
    void selfScopeShouldFilterByUserId() {
        DataScopeContext.set(new DataScopeContext.Scope(7L, DataScopeResult.self()));

        Condition condition = DataScopeConditions.of(SqlTables.SYS_ONLINE_SESSION, null, "user_id");

        assertTrue(condition.sql().contains("user_id = ?"));
        assertEquals(List.of(7L), condition.binds());
    }

    @Test
    void orgScopeShouldFilterByVisibleUserIds() {
        DataScopeContext.set(new DataScopeContext.Scope(7L, DataScopeResult.ofOrgs(Set.of(1L, 2L))));

        Condition condition = DataScopeConditions.of(SqlTables.SYS_ONLINE_SESSION, null, "user_id");

        assertTrue(condition.sql().contains("sys_user_org"));
        assertTrue(condition.binds().containsAll(List.of(1L, 2L)));
    }

    @Test
    void missingContextShouldNotFilter() {
        Condition condition = DataScopeConditions.of(SqlTables.SYS_ONLINE_SESSION, null, "user_id");

        assertEquals("1 = 1", condition.sql());
        assertTrue(condition.binds().isEmpty());
    }
}
