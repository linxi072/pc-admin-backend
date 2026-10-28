package com.acme.scaffold.job.domain;

import com.acme.scaffold.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JobDagRules} 纯函数单元测试（零 Spring / 零 DB）。
 *
 * <p>覆盖：{@code depends_on} 的解析/序列化（含容错与去重保序）、依赖存在性/自依赖校验、
 * 环检测（DFS 回边）、拓扑排序（Kahn，依赖前置），以及底层去重工具。
 */
class JobDagRulesTest {

    // ------------------------------------------------------------------
    // parseDependsOn
    // ------------------------------------------------------------------

    @Test
    void parseDependsOnNormalArray() {
        assertEquals(List.of(101L, 102L), JobDagRules.parseDependsOn("[101,102]"));
    }

    @Test
    void parseDependsOnDedupesAndKeepsOrder() {
        // 去重保序：保留首次出现顺序 -> [3,1,2]
        assertEquals(List.of(3L, 1L, 2L), JobDagRules.parseDependsOn("[3,1,2,1,3]"));
    }

    @Test
    void parseDependsOnIgnoresNonNumeric() {
        assertEquals(List.of(5L), JobDagRules.parseDependsOn("[5,\"x\",true]"));
    }

    @Test
    void parseDependsOnEmptyAndMalformedReturnEmpty() {
        assertTrue(JobDagRules.parseDependsOn("[]").isEmpty());
        assertTrue(JobDagRules.parseDependsOn("").isEmpty());
        assertTrue(JobDagRules.parseDependsOn(null).isEmpty());
        assertTrue(JobDagRules.parseDependsOn("not json").isEmpty());
        // 非数组（对象 / 数字）也视为无依赖
        assertTrue(JobDagRules.parseDependsOn("{}").isEmpty());
        assertTrue(JobDagRules.parseDependsOn("123").isEmpty());
    }

    // ------------------------------------------------------------------
    // serializeDependsOn
    // ------------------------------------------------------------------

    @Test
    void serializeDependsOnWritesArray() {
        assertEquals("[101,102]", JobDagRules.serializeDependsOn(List.of(101L, 102L)));
    }

    @Test
    void serializeDependsOnDedupes() {
        assertEquals("[1,2]", JobDagRules.serializeDependsOn(List.of(1L, 2L, 1L)));
    }

    @Test
    void serializeDependsOnEmptyReturnsNull() {
        assertEquals(null, JobDagRules.serializeDependsOn(List.of()));
        assertEquals(null, JobDagRules.serializeDependsOn(null));
    }

    // ------------------------------------------------------------------
    // validateDependencies
    // ------------------------------------------------------------------

    @Test
    void validateDependenciesNullAndEmptyPasses() {
        JobDagRules.validateDependencies(10L, null, Set.of(1L, 2L));
        JobDagRules.validateDependencies(10L, List.of(), Set.of(1L, 2L));
    }

    @Test
    void validateDependenciesSelfThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> JobDagRules.validateDependencies(10L, List.of(10L), Set.of(10L)));
        assertTrue(ex.getMessage().contains("自身"));
    }

    @Test
    void validateDependenciesNonExistentThrows() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> JobDagRules.validateDependencies(10L, List.of(99L), Set.of(1L, 2L)));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void validateDependenciesExistingPasses() {
        JobDagRules.validateDependencies(10L, List.of(1L, 2L), Set.of(1L, 2L, 10L));
    }

    @Test
    void validateDependenciesNullExistingSetSkipsExistence() {
        // existingIds 为 null 时跳过存在性校验（仅拦自依赖）
        JobDagRules.validateDependencies(10L, List.of(99L), null);
    }

    // ------------------------------------------------------------------
    // hasCycle
    // ------------------------------------------------------------------

    private Function<Long, List<Long>> depsOf(java.util.Map<Long, List<Long>> edges) {
        return id -> edges.getOrDefault(id, List.of());
    }

    @Test
    void hasCycleDetectsThreeNodeCycle() {
        // 20 -> 10 -> 30 -> 20
        var edges = java.util.Map.of(20L, List.of(10L), 10L, List.of(30L), 30L, List.of(20L));
        assertTrue(JobDagRules.hasCycle(20L, depsOf(edges)));
    }

    @Test
    void hasCycleDetectsTwoNodeCycle() {
        var edges = java.util.Map.of(10L, List.of(20L), 20L, List.of(10L));
        assertTrue(JobDagRules.hasCycle(10L, depsOf(edges)));
    }

    @Test
    void hasCycleNoCycleReturnsFalse() {
        // 10 -> 20 -> 30（无环）
        var edges = java.util.Map.of(10L, List.of(20L), 20L, List.of(30L));
        assertTrue(!JobDagRules.hasCycle(10L, depsOf(edges)));
    }

    // ------------------------------------------------------------------
    // topologicalOrder
    // ------------------------------------------------------------------

    @Test
    void topologicalOrderPutsDependenciesFirst() {
        // a 依赖 b，b 依赖 c：期望 c 在 b 前、b 在 a 前
        var edges = java.util.Map.of(1L, List.of(2L), 2L, List.of(3L));
        List<Long> order = JobDagRules.topologicalOrder(List.of(1L, 2L, 3L), depsOf(edges));
        assertEquals(3, order.size());
        assertTrue(order.indexOf(3L) < order.indexOf(2L));
        assertTrue(order.indexOf(2L) < order.indexOf(1L));
    }

    // ------------------------------------------------------------------
    // dedupe
    // ------------------------------------------------------------------

    @Test
    void dedupeRemovesNullsAndKeepsOrder() {
        // List.of 不允许 null，改用 Arrays.asList 构造含 null 的输入
        assertEquals(List.of(1L, 2L), JobDagRules.dedupe(Arrays.asList(1L, null, 2L, 1L)));
        assertTrue(JobDagRules.dedupe(null).isEmpty());
        assertTrue(JobDagRules.dedupe(List.of()).isEmpty());
    }
}
