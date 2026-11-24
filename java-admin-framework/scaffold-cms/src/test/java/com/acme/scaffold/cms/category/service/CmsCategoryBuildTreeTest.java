package com.acme.scaffold.cms.category.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.acme.scaffold.cms.category.domain.CategoryNode;
import com.acme.scaffold.cms.category.domain.CategoryView;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link CmsCategoryService#buildTree} 纯函数单测：层级组装、排序、孤儿升根与成环守卫。
 *
 * <p>迁移到 {@code TreeBuilder} 时重点回归两条与既有实现逐一对齐的语义：
 * 「父节点不存在的孤儿栏目升为根（不丢弃）」与「环数据不无限递归」。</p>
 */
class CmsCategoryBuildTreeTest {

    private static CategoryView view(long id, Long parentId, String name, Integer sortNo) {
        CategoryView v = new CategoryView();
        v.setId(id);
        v.setParentId(parentId);
        v.setCategoryCode("code-" + id);
        v.setCategoryName(name);
        v.setSortNo(sortNo);
        v.setStatus("ENABLED");
        return v;
    }

    /** 常规三层树：输入乱序，输出按 sortNo 升序组装层级。 */
    @Test
    void shouldAssembleHierarchyRegardlessOfInputOrder() {
        List<CategoryNode> roots = CmsCategoryService.buildTree(List.of(
                view(3, 1L, "子A", 1),
                view(1, null, "根", 0),
                view(2, 1L, "子B", 0),
                view(4, 2L, "孙", 0)));
        assertEquals(1, roots.size());
        CategoryNode root = roots.get(0);
        assertEquals("根", root.getCategoryName());
        assertEquals(2, root.getChildren().size());
        assertEquals("子B", root.getChildren().get(0).getCategoryName());
        // "孙"(parent=2) 挂在"子B"下，而非"子A"；按 sortNo 排序后子B 在 children[0]
        assertEquals(1, root.getChildren().get(0).getChildren().size());
        assertEquals("孙", root.getChildren().get(0).getChildren().get(0).getCategoryName());
    }

    /** 排序键 null 视为 0，同级按 sortNo 再按名称升序。 */
    @Test
    void shouldSortSiblingsBySortNoThenName() {
        List<CategoryNode> roots = CmsCategoryService.buildTree(List.of(
                view(1, null, "beta", null),
                view(2, null, "alpha", 1),
                view(3, null, "aaa", 1)));
        assertEquals(List.of("beta", "aaa", "alpha"),
                roots.stream().map(CategoryNode::getCategoryName).toList());
    }

    /** 孤儿栏目（父节点不在数据中）升为根，不丢弃——与迁移前实现语义一致。 */
    @Test
    void shouldPromoteOrphanToRoot() {
        List<CategoryNode> roots = CmsCategoryService.buildTree(List.of(
                view(1, null, "根", 0),
                view(9, 999L, "孤儿", 1)));
        assertEquals(2, roots.size());
        assertEquals("孤儿", roots.get(1).getCategoryName());
        assertTrue(roots.get(1).getChildren().isEmpty());
    }

    /** parent_id=0 视为根。 */
    @Test
    void shouldTreatZeroParentIdAsRoot() {
        List<CategoryNode> roots = CmsCategoryService.buildTree(List.of(view(1, 0L, "根", 0)));
        assertEquals(1, roots.size());
        assertTrue(roots.get(0).getChildren().isEmpty());
    }

    /** 数据被污染成环：互为父子且父节点均存在，双方都不是根 → 输出空树（与迁移前实现语义一致，不抛异常）。 */
    @Test
    void cycleShouldReturnEmptyRootsLikeLegacyImplementation() {
        List<CategoryNode> roots = CmsCategoryService.buildTree(List.of(
                view(1, 2L, "甲", 0),
                view(2, 1L, "乙", 1)));
        assertTrue(roots.isEmpty());
    }

    /** 环 + 根混合：路径守卫保证有根时正常展开、环上重复节点跳过，不无限递归。 */
    @Test
    void cycleWithRootShouldTerminate() {
        List<CategoryNode> roots = CmsCategoryService.buildTree(List.of(
                view(1, null, "根", 0),
                view(2, 3L, "甲", 1),
                view(3, 2L, "乙", 2)));
        assertEquals(1, roots.size());
        assertTrue(roots.get(0).getChildren().isEmpty());
    }

    /** 空输入返回空树。 */
    @Test
    void emptyInputShouldReturnEmptyList() {
        assertTrue(CmsCategoryService.buildTree(List.of()).isEmpty());
    }
}
