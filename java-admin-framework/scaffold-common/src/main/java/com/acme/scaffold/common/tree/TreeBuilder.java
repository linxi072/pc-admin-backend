package com.acme.scaffold.common.tree;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 泛型树构建器：把按展示序排列的平铺数据组装为父子层级结构（零依赖纯函数）。
 *
 * <p>集中此前提炼自 {@code MenuService#buildChildren} 与 {@code OrgService#toTree} 的两套同构递归：
 * 按 {@code parentIdOf} 分组（分组内保持平铺数据的输入顺序，故调用方须先按展示序排序），
 * 自 {@code rootPredicate} 判定的顶层节点递归挂载子节点。节点 → VO 的映射由 {@code mapper}
 * 完成，构建器不关心元素类型，菜单树 / 机构树 / 后续其它层级数据均可复用。</p>
 *
 * <p>健壮性：递归路径携带已访问集合，万一数据存在环（如防环校验被绕过）不会无限递归，
 * 环上的重复节点直接跳过（与既有实现的容错语义一致，不抛异常）。</p>
 *
 * <p>用法（机构树「父节点被数据权限过滤掉时升为根」的森林语义）：
 * <pre>{@code
 * List<OrgTreeVO> forest = TreeBuilder.build(all, SysOrgDO::getId,
 *         o -> o.getParentId() == null ? 0L : o.getParentId(),
 *         o -> { Long p = ...; return p == 0L || !visible.contains(p); },
 *         (node, children) -> new OrgTreeVO(..., children));
 * }</pre>
 */
public final class TreeBuilder {

    private TreeBuilder() {
    }

    /**
     * 构建树（或森林）。
     *
     * @param flat          平铺数据，须按展示序排列（分组保序依赖输入顺序）
     * @param idOf          取节点 id
     * @param parentIdOf    取父节点 id（顶层判定由 {@code rootPredicate} 负责，此处仅用于分组）
     * @param rootPredicate 是否为顶层节点（森林语义下「父不可见升根」也在此判定）
     * @param mapper        (节点, 已构建的子节点列表) → 目标 VO；子列表为空时传 {@code List.of()}
     * @param <N>           平铺元素类型
     * @param <V>           目标 VO 类型
     * @return 顶层节点列表（顺序同 {@code flat} 中输入顺序）
     */
    public static <N, V> List<V> build(List<N> flat,
                                       Function<N, Long> idOf,
                                       Function<N, Long> parentIdOf,
                                       Predicate<N> rootPredicate,
                                       BiFunction<N, List<V>, V> mapper) {
        if (flat == null || flat.isEmpty()) {
            return List.of();
        }
        Map<Long, List<N>> byParent = flat.stream()
                .collect(Collectors.groupingBy(parentIdOf));
        List<V> roots = new ArrayList<>();
        for (N node : flat) {
            if (rootPredicate.test(node)) {
                // 顶层节点自身先入路径：森林谓词把环上双方都判为顶层时，
                // 内层递归遇到路径上的顶层节点直接跳过，环不会展开出多余层级
                java.util.Set<N> path = Collections.newSetFromMap(new IdentityHashMap<>());
                path.add(node);
                roots.add(mapper.apply(node, childrenOf(idOf.apply(node), byParent, idOf, mapper, path)));
            }
        }
        return roots;
    }

    private static <N, V> List<V> childrenOf(Long parentId,
                                             Map<Long, List<N>> byParent,
                                             Function<N, Long> idOf,
                                             BiFunction<N, List<V>, V> mapper,
                                             java.util.Set<N> path) {
        List<N> children = byParent.getOrDefault(parentId, List.of());
        if (children.isEmpty()) {
            return List.of();
        }
        List<V> result = new ArrayList<>(children.size());
        for (N child : children) {
            // 环防护：同一条递归路径上重复出现的节点跳过（ IdentityHashMap 按身份去重，
            // 不依赖 equals/hashCode，避免 DO 重写 equals 带来的误判）
            if (!path.add(child)) {
                continue;
            }
            result.add(mapper.apply(child, childrenOf(idOf.apply(child), byParent, idOf, mapper, path)));
            path.remove(child);
        }
        return result;
    }

    /**
     * 深度优先展平树（或森林）为平铺列表：父节点先于其子节点，同级保持输入顺序。
     *
     * <p>与 {@link #build} 互为逆操作：build 把平铺数据组装为层级 VO，flatten 把层级 VO 还原为
     * 「父先于子」的平铺序列。典型场景为导出（部门树导出为扁平表格）与按编码反查
     * （遍历全树建 code→id 映射）。递归深度为树高（通常 <10），无栈溢出风险。</p>
     *
     * @param roots      顶层节点列表（{@code null} 视为空森林）
     * @param childrenOf 取子节点列表
     * @param <V>        节点类型
     * @return 平铺节点列表（父先于子，同级保序）
     */
    public static <V> List<V> flatten(List<V> roots, Function<V, List<V>> childrenOf) {
        List<V> out = new ArrayList<>();
        flattenInto(roots, childrenOf, out);
        return out;
    }

    private static <V> void flattenInto(List<V> nodes, Function<V, List<V>> childrenOf, List<V> out) {
        if (nodes == null) {
            return;
        }
        for (V node : nodes) {
            out.add(node);
            flattenInto(childrenOf.apply(node), childrenOf, out);
        }
    }
}
