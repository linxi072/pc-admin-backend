package com.acme.scaffold.ai.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** AI 语义查询解析单测（E9 探索）：实体识别、筛选提取、排序方向、分页钳制与 fail-open。 */
class SemanticQueryRulesTest {

    @Test
    void shouldParseChineseQuery() {
        var q = SemanticQueryRules.parse("查询用户 姓名=张三 创建时间=2026-01-01 按创建时间降序");
        assertEquals("用户", q.entity());
        assertEquals(2, q.filters().size());
        assertEquals("张三", q.filters().get(0).value());
        assertEquals(SemanticQueryRules.Operator.EQ, q.filters().get(0).operator());
        assertEquals("createdAt", q.filters().get(1).field());
        assertEquals("createdAt", q.orderBy());
        assertEquals("DESC", q.direction());
    }

    @Test
    void shouldExtractLikeAndColonForms() {
        List<SemanticQueryRules.Filter> filters = SemanticQueryRules.extractFilters("name:张% dept：研发 status=ENABLED");
        assertEquals(3, filters.size());
        assertEquals("name", filters.get(0).field());
        assertEquals(SemanticQueryRules.Operator.LIKE, filters.get(0).operator());
        assertEquals("张", filters.get(0).value());
        assertEquals("dept", filters.get(1).field());
        assertEquals("研发", filters.get(1).value());
    }

    @Test
    void shouldDetectEntityAndOrder() {
        assertEquals("role", SemanticQueryRules.parse("list roles").entity());
        assertEquals("ASC", SemanticQueryRules.parse("按创建时间升序").direction());
        assertTrue(SemanticQueryRules.parse("随便问问").entity().isEmpty());
    }

    @Test
    void shouldSanitizePaginationAndDirection() {
        var q = SemanticQueryRules.sanitize(new SemanticQueryRules.SemanticQuery(
                "user", List.of(), "createdAt", "desc", 0, 9999));
        assertEquals(1, q.page());
        assertEquals(200, q.size());
        assertEquals("DESC", q.direction());
        var safe = SemanticQueryRules.sanitize(null);
        assertEquals("", safe.entity());
        assertEquals(20, safe.size());
    }

    @Test
    void shouldFailOpenOnNullAndNormalizeFields() {
        var q = SemanticQueryRules.parse(null);
        assertTrue(q.filters().isEmpty());
        assertEquals("", q.entity());
        assertEquals("createdAt", SemanticQueryRules.normalizeField("更新时间"));
        assertEquals("status", SemanticQueryRules.normalizeField(" Status "));
    }
}
