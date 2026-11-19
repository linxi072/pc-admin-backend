package com.acme.scaffold.common.tree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 泛型树构建器测试：父子分组保序、森林语义（父不可见升根）、空输入与成环守卫（零依赖离线）。
 */
class TreeBuilderTest {

    /** 测试节点：id + parentId，VO 形态为 id + 子列表。 */
    record Node(Long id, Long parentId) {
    }

    record Vo(Long id, List<Vo> children) {
    }

    private static Vo vo(Node n, List<Vo> children) {
        return new Vo(n.id(), children);
    }

    private static List<Vo> build(List<Node> flat) {
        return TreeBuilder.build(flat, Node::id, Node::parentId,
                n -> n.parentId() == 0L, TreeBuilderTest::vo);
    }

    @Test
    void shouldBuildHierarchyFromFlatData() {
        List<Vo> tree = build(List.of(
                new Node(1L, 0L),
                new Node(11L, 1L),
                new Node(12L, 1L),
                new Node(121L, 12L),
                new Node(2L, 0L)));

        assertEquals(2, tree.size());
        assertEquals(1L, tree.get(0).id());
        assertEquals(List.of(11L, 12L), tree.get(0).children().stream().map(Vo::id).toList());
        assertEquals(List.of(121L),
                tree.get(0).children().get(1).children().stream().map(Vo::id).toList());
        assertTrue(tree.get(1).children().isEmpty());
    }

    @Test
    void shouldPreserveInputOrderWithinGroupAndRoots() {
        // 同父节点的子节点按平铺输入顺序；顶层节点也按输入顺序
        List<Vo> tree = build(List.of(
                new Node(2L, 0L),
                new Node(1L, 0L),
                new Node(22L, 2L),
                new Node(21L, 2L)));
        assertEquals(List.of(2L, 1L), tree.stream().map(Vo::id).toList());
        assertEquals(List.of(22L, 21L), tree.get(0).children().stream().map(Vo::id).toList());
    }

    @Test
    void forestSemanticsShouldPromoteNodeWithInvisibleParentToRoot() {
        // 机构树场景：父节点 1 被数据权限过滤（rootPredicate 判非根的节点 11 升根）
        List<Node> flat = List.of(new Node(1L, 0L), new Node(11L, 1L));
        List<Vo> forest = TreeBuilder.build(flat, Node::id, Node::parentId,
                // 节点 1 可见为根；节点 11 的父 1 被过滤 → 升根
                n -> Objects.equals(n.id(), 1L) || Objects.equals(n.id(), 11L),
                TreeBuilderTest::vo);
        assertEquals(List.of(1L, 11L), forest.stream().map(Vo::id).toList());
        assertTrue(forest.get(1).children().isEmpty());
    }

    @Test
    void nullOrEmptyInputShouldReturnEmptyList() {
        assertEquals(List.of(), TreeBuilder.build(null, Node::id, Node::parentId,
                n -> true, TreeBuilderTest::vo));
        assertEquals(List.of(), build(List.of()));
    }

    @Test
    void cycleShouldTerminateWithoutInfiniteRecursion() {
        // 数据被污染成环：a 的父是 b、b 的父是 a，两者均被判顶层。
        // 路径守卫保证：每个根只挂一层对方节点，孙层被跳过，递归必然终止（不抛异常）。
        Node a = new Node(1L, 2L);
        Node b = new Node(2L, 1L);
        List<Vo> forest = TreeBuilder.build(List.of(a, b), Node::id, Node::parentId,
                n -> true, TreeBuilderTest::vo);
        assertEquals(2, forest.size());
        for (Vo root : forest) {
            assertEquals(1, root.children().size());
            assertTrue(root.children().get(0).children().isEmpty());
        }
    }

    @Test
    void orphanNodesShouldBeIgnoredWhenNotRoot() {
        // 父 id 无对应顶层判定（父节点不存在且节点不被判根）→ 既非根也无挂载点，不出现在结果中
        List<Vo> tree = build(List.of(
                new Node(1L, 0L),
                new Node(99L, 42L)));
        assertEquals(1, tree.size());
        assertEquals(1L, tree.get(0).id());
    }
}
