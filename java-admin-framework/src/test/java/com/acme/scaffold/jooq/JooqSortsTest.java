package com.acme.scaffold.jooq;

import com.acme.scaffold.common.api.PageQuery;
import com.acme.scaffold.common.api.SortItem;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JooqSorts} 单元测试：白名单映射、忽略非白名单/不存在列、去重、最多 3 字段、id 兜底。
 * 不依赖数据库：用 {@code DSL.using(SQLDialect.MYSQL)} 仅做 SQL 渲染断言。
 */
class JooqSortsTest {

    private final DSLContext ctx = DSL.using(SQLDialect.MYSQL);
    private final Map<String, String> whitelist = JooqSorts.whitelist(
            "id", "id", "username", "username", "createdAt", "created_at");

    private String render(OrderField<?>[] order) {
        return ctx.renderInlined(ctx.selectOne().from(JooqTables.SYS_USER.table()).orderBy(order))
                .toString().toLowerCase().replace("`", "");
    }

    @Test
    void whitelistRequiresEvenPairs() {
        assertThrows(IllegalArgumentException.class, () -> JooqSorts.whitelist("id"));
    }

    @Test
    void mapsWhitelistedFieldAndDirection() {
        PageQuery q = PageQuery.of(1, 10, "username", "DESC");
        String sql = render(JooqSorts.resolve(JooqTables.SYS_USER, q, whitelist,
                JooqTables.SYS_USER.field("id", Object.class).desc()));
        assertTrue(sql.contains("username"), sql);
        assertTrue(sql.contains("desc"), sql);
    }

    @Test
    void ignoresFieldNotInWhitelist() {
        PageQuery q = PageQuery.of(1, 10, "password_hash", "ASC");
        String sql = render(JooqSorts.resolve(JooqTables.SYS_USER, q, whitelist,
                JooqTables.SYS_USER.field("id", Object.class).desc()));
        assertFalse(sql.contains("password_hash"), sql);
        // 回落默认 id desc
        assertTrue(sql.contains("id"), sql);
    }

    @Test
    void ignoresColumnNotInTable() {
        // 白名单里映射到一个表上根本不存在的列 -> 忽略
        Map<String, String> bogus = JooqSorts.whitelist("missing", "no_such_column");
        PageQuery q = PageQuery.of(1, 10, "missing", "ASC");
        String sql = render(JooqSorts.resolve(JooqTables.SYS_USER, q, bogus,
                JooqTables.SYS_USER.field("id", Object.class).desc()));
        assertFalse(sql.contains("no_such_column"), sql);
    }

    @Test
    void dedupesAndCapsAtThree() {
        List<SortItem> items = List.of(
                new SortItem("username", "ASC"),
                new SortItem("username", "ASC"),  // 重复 -> 去重
                new SortItem("id", "ASC"),
                new SortItem("display_name", "ASC"), // 不在白名单
                new SortItem("status", "ASC"));      // 不在白名单
        PageQuery q = new PageQuery(1, 10, items);
        String sql = render(JooqSorts.resolve(JooqTables.SYS_USER, q, whitelist,
                JooqTables.SYS_USER.field("id", Object.class).desc()));
        assertTrue(sql.contains("username"), sql);
        // id 已在白名单命中，兜底不再重复追加
        int idCount = countOccurrences(sql, "id ");
        assertTrue(idCount <= 1, "id 至多出现一次, got: " + sql);
    }

    @Test
    void appendsIdFallbackWhenNotUsed() {
        PageQuery q = PageQuery.of(1, 10, "username", "ASC");
        String sql = render(JooqSorts.resolve(JooqTables.SYS_USER, q, whitelist)); // 无默认
        assertTrue(sql.contains("username"), sql);
        assertTrue(sql.contains("id"), sql);
    }

    @Test
    void emptySortsReturnsDefaults() {
        OrderField<?> def = JooqTables.SYS_USER.field("id", Object.class).desc();
        OrderField<?>[] out = JooqSorts.resolve(JooqTables.SYS_USER, PageQuery.of(1, 10), whitelist, def);
        assertEquals(1, out.length);
    }

    private int countOccurrences(String s, String sub) {
        int c = 0, i = 0;
        while ((i = s.indexOf(sub, i)) >= 0) {
            c++;
            i += sub.length();
        }
        return c;
    }
}
