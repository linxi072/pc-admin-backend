package com.acme.scaffold.common.audit;

import com.acme.scaffold.common.audit.dto.AuditLogQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.conf.ParamType;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockDataProvider;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计日志聚合查询回归（E2E F1/F3 的性能与 500 修复）。
 *
 * <p>锁定两条关键约束（无需真实数据库，用 jOOQ {@link MockConnection} 捕获渲染后的 SQL）：</p>
 * <ol>
 *   <li><b>统计摘要只发一条聚合 SQL</b>：原实现是 4 次 {@code count(*)} + 1 次 {@code avg/max}，
 *       共 5 次全表扫描，在 1200 万行的 {@code sys_operation_log} 上耗时 16.3s 并触发前端
 *       15s 超时；同时 {@code max(duration_ms)} 因列是 {@code BIGINT UNSIGNED} 而以 Long 读取失败，
 *       接口直接 500。</li>
 *   <li><b>「最近动态」不发 count</b>：该面板只要最近 N 条，{@code count(*)} 会把端点拖到 11.9s。</li>
 * </ol>
 *
 * <p>注意：Mock 层不经过 JDBC 类型转换，因此本测试无法直接断言「无符号列读取」，
 * 该点由 {@code AuditLogServiceImpl#summary} 把 avg/max 字段声明为 {@code BigDecimal} 保证
 * （见方法内注释）。</p>
 */
class AuditLogAggregationQueryTest {

    private final List<String> executedSql = new ArrayList<>();
    private final List<Object[]> executedBindings = new ArrayList<>();

    /** 预置的聚合结果行；默认给一条全 0 的 5 列结果，避免 Mock 返回无列的 0 行结果导致读取失败。 */
    private Result<Record> cannedAggregate;

    private AuditLogServiceImpl service;

    @BeforeEach
    void init() {
        executedSql.clear();
        executedBindings.clear();
        cannedAggregate = aggregateResult(0, 0, 0, "0", "0");
        MockDataProvider provider = new MockDataProvider() {
            @Override
            public MockResult[] execute(MockExecuteContext ctx) {
                executedSql.add(ctx.sql() != null ? ctx.sql() : "");
                executedBindings.add(ctx.bindings() != null ? ctx.bindings() : new Object[0]);
                if (isAggregate(ctx.sql())) {
                    return new MockResult[]{ new MockResult(1, cannedAggregate) };
                }
                return new MockResult[]{ new MockResult(0) };
            }
        };
        DSLContext dsl = DSL.using(new MockConnection(provider), SQLDialect.MYSQL,
                new Settings().withParamType(ParamType.INDEXED));
        service = new AuditLogServiceImpl(dsl, Runnable::run, new ObjectMapper());
    }

    /** 构造与 summary() 聚合列一一对应的单行结果（列名仅用于断言可读性）。 */
    private static Result<Record> aggregateResult(int total, int success, int slow, String avg, String max) {
        DSLContext plain = DSL.using(SQLDialect.MYSQL);
        Field<Integer> totalF = DSL.field("total", Integer.class);
        Field<BigDecimal> successF = DSL.field("success_count", BigDecimal.class);
        Field<BigDecimal> slowF = DSL.field("slow_count", BigDecimal.class);
        Field<BigDecimal> avgF = DSL.field("avg_duration", BigDecimal.class);
        Field<BigDecimal> maxF = DSL.field("max_duration", BigDecimal.class);
        Field<?>[] fields = new Field<?>[]{totalF, successF, slowF, avgF, maxF};
        Result<Record> result = plain.newResult(fields);
        Record row = plain.newRecord(fields);
        row.set(totalF, total);
        row.set(successF, BigDecimal.valueOf(success));
        row.set(slowF, BigDecimal.valueOf(slow));
        row.set(avgF, new BigDecimal(avg));
        row.set(maxF, new BigDecimal(max));
        result.add(row);
        return result;
    }

    /** 最近一次执行的语句里是否绑定了指定值（LIMIT 走索引参数，不在 SQL 文本中）。 */
    private boolean lastBinds(Object value) {
        int last = executedBindings.size() - 1;
        for (Object b : executedBindings.get(last)) {
            if (value instanceof Number want && b instanceof Number got && want.longValue() == got.longValue()) {
                return true;
            }
            if (value.equals(b)) {
                return true;
            }
        }
        return false;
    }

    /** 简易判定：聚合摘要语句含 avg( / max(（最近动态的 SELECT 不含这两个函数）。 */
    private boolean isAggregate(String sql) {
        String s = sql == null ? "" : sql.toLowerCase();
        return s.contains("avg(") && s.contains("max(");
    }

    private static AuditLogQuery allRange() {
        return new AuditLogQuery(1, 10, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    @Test
    @DisplayName("summary：五项指标合并为单条聚合 SQL（不再 5 次全表扫描）")
    void summaryIssuesSingleAggregateStatement() {
        service.summary(allRange(), true);

        assertEquals(1, executedSql.size(), "统计摘要必须只发一条 SQL，实际发出 " + executedSql.size() + " 条");
        String sql = executedSql.get(0).toLowerCase();
        assertTrue(sql.contains("count("), "应包含 count(*) 统计总数");
        assertTrue(sql.contains("sum("), "成功数/慢操作数应通过 sum(case when ...) 在同一次扫描中统计");
        assertTrue(sql.contains("avg("), "应包含 avg(duration_ms)");
        assertTrue(sql.contains("max("), "应包含 max(duration_ms)");
    }

    @Test
    @DisplayName("summary：单条聚合结果正确映射为摘要（总/成功/失败/慢/均值/峰值）")
    void summaryMapsAggregateRow() {
        cannedAggregate = aggregateResult(100, 90, 7, "12.6", "5000");

        AuditLogSummary summary = service.summary(allRange(), true);

        assertEquals(100L, summary.total());
        assertEquals(90L, summary.successCount());
        assertEquals(10L, summary.failureCount(), "失败数应由 total - successCount 推导");
        assertEquals(7L, summary.slowCount());
        assertEquals(12L, summary.avgDurationMs(), "平均耗时向下取整（与原口径一致）");
        assertEquals(5000L, summary.maxDurationMs());
        assertTrue(summary.sensitiveVisible(), "脱敏可见标识应透传");
    }

    @Test
    @DisplayName("recentActivities：不发 count(*)，单条带 limit 的查询且按 occurred_at/id 倒序")
    void recentActivitiesSkipsCount() {
        service.recentActivities(42L, 8, false);

        assertEquals(1, executedSql.size(), "最近动态应只发一条 SQL，实际 " + executedSql.size() + " 条");
        String sql = executedSql.get(0).toLowerCase();
        assertFalse(sql.contains("count("), "最近动态不需要 total，不得发 count(*)（F1 根因）");
        assertTrue(sql.contains("limit"), "必须把条数下推为 LIMIT");
        assertTrue(sql.contains("order by"), "必须由数据库排序，不能全量拉取后内存排序");
        assertTrue(sql.contains("occurred_at") && sql.contains("desc"), "默认按发生时间倒序");
    }

    @Test
    @DisplayName("recentActivities：limit 越界钳制到 1..50")
    void recentActivitiesClampsLimit() {
        service.recentActivities(null, 9999, false);
        assertTrue(lastBinds(50), "limit 上限应钳制为 50，实际绑定：" + java.util.Arrays.toString(executedBindings.get(executedBindings.size() - 1)));

        service.recentActivities(null, 0, false);
        assertTrue(lastBinds(1), "limit 下界应钳制为 1，实际绑定：" + java.util.Arrays.toString(executedBindings.get(executedBindings.size() - 1)));
    }
}
