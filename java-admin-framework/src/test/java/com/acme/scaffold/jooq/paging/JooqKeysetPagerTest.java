package com.acme.scaffold.jooq.paging;

import com.acme.scaffold.common.api.CursorCodec;
import com.acme.scaffold.common.api.dto.KeysetPageQuery;
import com.acme.scaffold.jooq.dsl.JooqTables;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.conf.ParamType;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JooqKeysetPagerTest {

    private final DSLContext dsl = DSL.using(SQLDialect.MYSQL);
    private final Field<?> createdAt = JooqTables.SYS_FILE.field("created_at", LocalDateTime.class);
    private final Field<?> id = JooqTables.SYS_FILE.field("id", Long.class);
    private final Field<?>[] keyFields = {createdAt, id};
    private final boolean[] ascending = {false, false}; // created_at DESC, id DESC

    @Test
    void firstPage_hasNoSeekAndUsesLimitPlusOne() {
        KeysetPageQuery q = KeysetPageQuery.of(20);
        var built = JooqKeysetPager.buildSelect(dsl, JooqTables.SYS_FILE,
                DSL.trueCondition(), q, keyFields, ascending);
        String sql = built.getSQL(ParamType.INLINED).toLowerCase();
        assertTrue(sql.contains("limit 21"), "深翻页应取 size+1 条以判定 hasMore: " + sql);
        assertFalse(sql.contains(">"), "首页不应出现 seek 行比较: " + sql);
        assertFalse(sql.contains("offset"), "keyset 不应出现 offset: " + sql);
    }

    @Test
    void withCursor_rendersSeekRowComparison() {
        String cursor = CursorCodec.encode(List.of("2026-10-15T21:01:28", 123L));
        KeysetPageQuery q = new KeysetPageQuery(20, cursor);
        var built = JooqKeysetPager.buildSelect(dsl, JooqTables.SYS_FILE,
                DSL.trueCondition(), q, keyFields, ascending);
        String sql = built.getSQL(ParamType.INLINED).toLowerCase();
        assertTrue(sql.contains("("), "应出现行比较括号: " + sql);
        assertTrue(sql.contains("created_at"), sql);
        assertTrue(sql.contains("id"), sql);
        assertTrue(sql.contains("limit 21"), sql);
    }

    @Test
    void cursorLengthMismatch_detectedByQuery() {
        // 构造一个 1 个字段的游标，但 keyFields 长度为 2 —— query 应抛校验异常
        KeysetPageQuery q = new KeysetPageQuery(20, CursorCodec.encode(List.of("2026-10-15T21:01:28")));
        try {
            JooqKeysetPager.query(dsl, JooqTables.SYS_FILE, Object.class,
                    DSL.trueCondition(), q, o -> new Object[0], keyFields, ascending);
            assertFalse(true, "游标字段数不匹配时应抛异常");
        } catch (com.acme.scaffold.common.exception.BusinessException e) {
            assertTrue(e.getMessage().contains("游标字段数"));
        }
    }
}
