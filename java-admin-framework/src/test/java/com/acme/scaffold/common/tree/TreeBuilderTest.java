package com.acme.scaffold.common.tree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TreeBuilder} 离线单测：层级组装 / 保序 / 森林语义 / 环防护 / 空数据。
 */
class TreeBuilderTest {

    /** 测试用平铺节点（id / parentId / 名称）。 */
    private record Node(Long id, Long parentId, String name) {
    }

    /** 测试用 VO（复用 record，子列表由构建器注入）。 */
    private record Vo(Long id, String name, List<Vo> children) {
    }

    private static Node node(Long id, Long parentId, String name) {
        return new Node(id, parentId, name);
    }

    private static Long parentIdOf(Node n) {
        return n.parentId() == null ? 0L : n.parentId();
    }

    private static Vo toVo(Node n, List<Vo> children) {
        return new Vo(n.id(), n.name(), children);
    }

    @Test
    @DisplayName("多层级：按 parentId 挂载，顶层为 parentId=0")
    void buildsMultiLevelHierarchy() {
        List<Node> flat = List.of(
                node(1L, 0L, "根"),
                node(2L, 1L, "子A"),
                node(3L, 1L, "子B"),
                node(4L, 2L, "孙A1"));

        List<Vo> tree = TreeBuilder.build(flat, Node::id, TreeBuilderTest::parentIdOf,
                n -> parentIdOf(n) == 0L, TreeBuilderTest::toVo);

        assertEquals(1, tree.size());
        Vo root = tree.get(0);
        assertEquals("根", root.name());
        assertEquals(List.of("子A", "子B"), root.children().stream().map(Vo::name).toList());
        Vo childA = root.children().get(0);
        assertEquals(1, childA.children().size());
        assertEquals("孙A1", childA.children().get(0).name());
        assertTrue(childA.children().get(0).children().isEmpty());
    }

    @Test
    @DisplayName("保序：同级顺序 = 平铺输入顺序（依赖调用方预排序）")
    void preservesInputOrderAmongSiblings() {
        List<Node> flat = new ArrayList<>(List.of(
                node(1L, 0L, "根"),
                node(30L, 1L, "C"),
                node(10L, 1L, "A"),
                node(20L, 1L, "B")));

        List<Vo> tree = TreeBuilder.build(flat, Node::id, TreeBuilderTest::parentIdOf,
                n -> parentIdOf(n) == 0L, TreeBuilderTest::toVo);

        assertEquals(List.of("C", "A", "B"),
                tree.get(0).children().stream().map(Vo::name).toList());
    }

    @Test
    @DisplayName("森林语义：顶层谓词判定多个根（父不可见升根）")
    void supportsForestViaRootPredicate() {
        List<Node> flat = List.of(
                node(1L, 0L, "可见根"),
                node(2L, 1L, "其子"),
                node(3L, 99L, "孤枝"),   // 父 99 不在数据内 → 谓词判为顶层
                node(4L, 3L, "孤枝之子"));
        List<Long> visible = List.of(1L, 2L, 3L, 4L);

        List<Vo> forest = TreeBuilder.build(flat, Node::id, TreeBuilderTest::parentIdOf,
                n -> {
                    Long p = parentIdOf(n);
                    return p == 0L || !visible.contains(p);
                },
                TreeBuilderTest::toVo);

        assertEquals(List.of("可见根", "孤枝"), forest.stream().map(Vo::name).toList());
        assertEquals(List.of("孤枝之子"), forest.get(1).children().stream().map(Vo::name).toList());
    }

    @Test
    @DisplayName("环防护：互为父子的节点均被判顶层时不无限递归，路径上重复节点被跳过")
    void toleratesCycle() {
        // 甲/乙互为父子（2 的父=3，3 的父=2）；数据权限过滤后（visible 为空集）
        // 二者都满足「父不可见升根」，成为森林的两个顶层
        List<Node> twoRing = List.of(
                node(2L, 3L, "甲"),
                node(3L, 2L, "乙"));
        List<Long> visible = List.of();

        List<Vo> forest = TreeBuilder.build(twoRing, Node::id, TreeBuilderTest::parentIdOf,
                n -> {
                    Long p = parentIdOf(n);
                    return p == 0L || !visible.contains(p);
                },
                TreeBuilderTest::toVo);

        // 甲、乙均为顶层；甲的子是乙，乙的子本应是甲但已在递归路径上 → 跳过，不递归
        assertEquals(List.of("甲", "乙"), forest.stream().map(Vo::name).toList());
        Vo jia = forest.get(0);
        assertEquals(1, jia.children().size());
        assertEquals("乙", jia.children().get(0).name());
        assertTrue(jia.children().get(0).children().isEmpty());
    }

    @Test
    @DisplayName("空数据：返回空列表")
    void emptyInputYieldsEmptyList() {
        assertTrue(TreeBuilder.build(List.of(), Node::id, TreeBuilderTest::parentIdOf,
                n -> true, TreeBuilderTest::toVo).isEmpty());
        assertTrue(TreeBuilder.build(null, Node::id, TreeBuilderTest::parentIdOf,
                n -> true, TreeBuilderTest::toVo).isEmpty());
    }

    @Test
    @DisplayName("叶子节点：children 为空列表（非 null）")
    void leafChildrenAreEmptyListNotNull() {
        List<Node> flat = List.of(node(1L, 0L, "仅根"));

        List<Vo> tree = TreeBuilder.build(flat, Node::id, TreeBuilderTest::parentIdOf,
                n -> parentIdOf(n) == 0L, TreeBuilderTest::toVo);

        assertEquals(1, tree.size());
        assertTrue(tree.get(0).children().isEmpty());
    }
}
