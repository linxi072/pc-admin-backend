package com.acme.scaffold.job.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.common.exception.BusinessException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 任务 DAG 纯函数单测：锁定 depends_on 序列化契约、依赖校验边界、环检测与拓扑序。
 * DAG 顺序错误会造成任务死锁或漏触发，故对关键契约逐条固化为回归测试。
 */
class JobDagRulesTest {

    @Test
    void parseShouldDedupePreserveOrderAndIgnoreNonNumeric() {
        assertEquals(
                List.of(3L, 1L, 2L),
                JobDagRules.parseDependsOn("[3,1,3,\"x\",null,2,true]"));
    }

    @Test
    void parseShouldReturnEmptyForNullOrMalformedInput() {
        assertTrue(JobDagRules.parseDependsOn(null).isEmpty());
        assertTrue(JobDagRules.parseDependsOn("   ").isEmpty());
        assertTrue(JobDagRules.parseDependsOn("not-json").isEmpty());
        assertTrue(JobDagRules.parseDependsOn("{\"a\":1}").isEmpty(), "非数组根节点应返回空列表");
    }

    @Test
    void serializeShouldReturnNullForEmptyAndDedupePreserveOrder() {
        assertNull(JobDagRules.serializeDependsOn(null));
        assertNull(JobDagRules.serializeDependsOn(List.of()), "空列表不写空数组，保持列为空");
        assertEquals("[3,1,2]", JobDagRules.serializeDependsOn(List.of(3L, 1L, 3L, 2L)));
    }

    @Test
    void serializeParseRoundTripShouldPreserveDependencyOrder() {
        List<Long> original = List.of(10L, 20L, 30L);
        assertEquals(original, JobDagRules.parseDependsOn(JobDagRules.serializeDependsOn(original)));
    }

    @Test
    void validateShouldAcceptValidEmptyAndNullTolerantInputs() {
        assertDoesNotThrow(() -> JobDagRules.validateDependencies(3L, List.of(1L, 2L), Set.of(1L, 2L, 3L)));
        assertDoesNotThrow(() -> JobDagRules.validateDependencies(null, null, Set.of()));
        assertDoesNotThrow(() -> JobDagRules.validateDependencies(1L, List.of(), Set.of()));
        // existingIds 为 null 时跳过存在性检查；null 元素被忽略
        assertDoesNotThrow(() -> JobDagRules.validateDependencies(9L, Arrays.asList(null, 1L), null));
    }

    @Test
    void validateShouldRejectSelfDependency() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> JobDagRules.validateDependencies(5L, List.of(1L, 5L), Set.of(1L, 5L)));
        assertTrue(ex.getMessage().contains("依赖自身"));
    }

    @Test
    void validateShouldRejectMissingDependency() {
        BusinessException ex = assertThrows(
                BusinessException.class,
                () -> JobDagRules.validateDependencies(5L, List.of(99L), Set.of(5L)));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void hasCycleShouldDetectDirectAndMultiHopCycles() {
        assertTrue(JobDagRules.hasCycle(1L, id -> List.of(1L)), "直接自依赖成环");
        Map<Long, List<Long>> graph = Map.of(
                1L, List.of(2L), 2L, List.of(3L), 3L, List.of(1L));
        assertTrue(JobDagRules.hasCycle(1L, graph::get), "多跳回边成环");
    }

    @Test
    void hasCycleShouldAcceptAcyclicAndNullDependencyLists() {
        Map<Long, List<Long>> acyclic = Map.of(
                1L, List.of(2L), 2L, List.of(3L), 3L, List.of());
        assertFalse(JobDagRules.hasCycle(1L, acyclic::get));
        assertFalse(JobDagRules.hasCycle(1L, id -> null), "依赖查询返回 null 应安全视为无边");
    }

    @Test
    void topologicalOrderShouldPlaceDependenciesBeforeDependents() {
        Map<Long, List<Long>> graph = Map.of(
                1L, List.of(), 2L, List.of(1L), 3L, List.of(2L, 1L));
        List<Long> order = JobDagRules.topologicalOrder(List.of(1L, 2L, 3L), graph::get);
        assertEquals(3, order.size());
        assertTrue(order.indexOf(1L) < order.indexOf(2L));
        assertTrue(order.indexOf(2L) < order.indexOf(3L));
    }

    @Test
    void topologicalOrderShouldIgnoreDependenciesOutsideNodeSet() {
        Map<Long, List<Long>> graph = Map.of(1L, List.of(), 2L, List.of(1L, 99L));
        assertEquals(
                List.of(1L, 2L),
                JobDagRules.topologicalOrder(List.of(1L, 2L), graph::get));
    }

    @Test
    void dedupeShouldIgnoreNullAndPreserveFirstOccurrenceOrder() {
        assertEquals(List.of(1L, 2L, 3L), JobDagRules.dedupe(Arrays.asList(1L, null, 2L, 1L, 3L)));
        assertTrue(JobDagRules.dedupe(null).isEmpty());
    }
}
