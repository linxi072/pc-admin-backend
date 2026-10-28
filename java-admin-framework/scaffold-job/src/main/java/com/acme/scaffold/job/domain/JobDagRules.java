package com.acme.scaffold.job.domain;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 任务依赖编排（DAG）纯函数层：解析、校验、环检测与拓扑排序。
 *
 * <p>设计约束（与 {@link JobValidator} 一致）：
 * <ul>
 *   <li>零 Spring / 零 DB 依赖，可离线单测；</li>
 *   <li>所有方法为 {@code static}，非法输入抛 {@link BusinessException#badRequest}；</li>
 *   <li>依赖以 {@code sys_job.depends_on}（JSON 数组文本）存储，本类负责其序列化/反序列化与图算法。</li>
 * </ul>
 *
 * <p>DAG 语义：任务 A 依赖 B 表示「B 成功执行后，才触发 A」。依赖不满足时 A 处于
 * {@link JobStatus#WAITING} 且不被自身调度；全部依赖成功后再由完成事件驱动一次即时触发。
 */
public final class JobDagRules {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JobDagRules() {
    }

    // ------------------------------------------------------------------
    // 序列化 / 反序列化
    // ------------------------------------------------------------------

    /** 解析 {@code depends_on} 文本为任务 ID 列表（去重保序，忽略非数字元素；空/非法返回空列表）。 */
    public static List<Long> parseDependsOn(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = JSON.readTree(json);
            if (!root.isArray()) {
                return List.of();
            }
            LinkedHashSet<Long> set = new LinkedHashSet<>();
            for (JsonNode node : root) {
                if (node.isNumber()) {
                    set.add(node.longValue());
                }
            }
            return List.copyOf(set);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 序列化依赖 ID 列表为 {@code depends_on} 文本；空列表返回 {@code null}（列保持为空，不写空数组）。 */
    public static String serializeDependsOn(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        List<Long> distinct = dedupe(ids);
        try {
            return JSON.writeValueAsString(distinct);
        } catch (Exception e) {
            throw BusinessException.badRequest("依赖任务列表序列化失败");
        }
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    /**
     * 校验依赖声明（创建 / 编辑共用）。
     *
     * @param selfId      当前任务 ID（用于自依赖检测；新建时可为 null）
     * @param deps        声明的依赖 ID 列表（可为空）
     * @param existingIds 当前所有任务 ID 集合（用于存在性校验；为 null 时跳过的存在性检查）
     */
    public static void validateDependencies(Long selfId, List<Long> deps, Set<Long> existingIds) {
        if (deps == null || deps.isEmpty()) {
            return;
        }
        for (Long dep : dedupe(deps)) {
            if (dep == null) {
                continue;
            }
            if (selfId != null && dep.equals(selfId)) {
                throw BusinessException.badRequest("任务不能依赖自身");
            }
            if (existingIds != null && !existingIds.contains(dep)) {
                throw BusinessException.badRequest("依赖的任务不存在: " + dep);
            }
        }
    }

    /**
     * 环检测：在「selfId 依赖 depsOf(selfId)」的前提下，若沿依赖边能回到 selfId 则存在环。
     *
     * @param selfId  当前任务 ID
     * @param depsOf  给定任务 ID 返回其（前向）依赖列表的函数（应覆盖已落库的边）
     * @return 是否存在环
     */
    public static boolean hasCycle(Long selfId, Function<Long, List<Long>> depsOf) {
        Set<Long> visited = new HashSet<>();
        for (Long dep : safe(depsOf.apply(selfId))) {
            if (!visited.add(dep)) {
                continue;
            }
            if (pathReaches(selfId, dep, depsOf, visited)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拓扑排序（Kahn 算法）：返回的列表中，任一任务的依赖总是排在其之前。
     * 存在环时返回尽力而为的顺序（未完全收敛），调用方应先以 {@link #hasCycle} 拦截环。
     *
     * @param nodeIds 待排序的任务 ID
     * @param depsOf  给定任务 ID 返回其（前向）依赖列表的函数
     */
    public static List<Long> topologicalOrder(List<Long> nodeIds, Function<Long, List<Long>> depsOf) {
        Map<Long, Integer> inDegree = new HashMap<>();
        Map<Long, List<Long>> dependents = new HashMap<>();
        for (Long id : nodeIds) {
            inDegree.putIfAbsent(id, 0);
        }
        for (Long id : nodeIds) {
            for (Long dep : safe(depsOf.apply(id))) {
                if (!inDegree.containsKey(dep)) {
                    continue; // 仅对给定集合内的节点排序
                }
                dependents.computeIfAbsent(dep, k -> new ArrayList<>()).add(id);
                inDegree.merge(id, 1, Integer::sum);
            }
        }
        Deque<Long> queue = new java.util.ArrayDeque<>();
        for (Map.Entry<Long, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }
        List<Long> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            Long cur = queue.poll();
            order.add(cur);
            for (Long next : dependents.getOrDefault(cur, List.of())) {
                int d = inDegree.merge(next, -1, Integer::sum);
                if (d == 0) {
                    queue.add(next);
                }
            }
        }
        return order;
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 去重保序（忽略 null）。 */
    public static List<Long> dedupe(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new LinkedHashSet<>(ids.stream().filter(java.util.Objects::nonNull).toList()));
    }

    private static boolean pathReaches(Long target, Long node, Function<Long, List<Long>> depsOf, Set<Long> visited) {
        if (target.equals(node)) {
            return true;
        }
        for (Long next : safe(depsOf.apply(node))) {
            if (next == null || !visited.add(next)) {
                continue;
            }
            if (pathReaches(target, next, depsOf, visited)) {
                return true;
            }
        }
        return false;
    }

    private static List<Long> safe(List<Long> list) {
        return list == null ? List.of() : list;
    }
}
