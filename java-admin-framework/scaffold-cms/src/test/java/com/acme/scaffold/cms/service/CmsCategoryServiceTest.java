package com.acme.scaffold.cms.service;

import com.acme.scaffold.cms.dto.CategoryCreateRequest;
import com.acme.scaffold.cms.dto.CategoryNode;
import com.acme.scaffold.cms.dto.CategoryView;
import com.acme.scaffold.cms.support.CmsH2;
import com.acme.scaffold.cms.support.StubSecurityContextFacade;
import com.acme.scaffold.common.exception.BusinessException;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 栏目服务集成测试：以 H2（MySQL 兼容模式）承载 DDL，直接实例化 Service（无 Spring 上下文）。
 */
class CmsCategoryServiceTest {

    private DSLContext dsl;
    private CmsCategoryService service;

    @BeforeEach
    void setUp() throws Exception {
        dsl = CmsH2.newContext();
        service = new CmsCategoryService(dsl, new StubSecurityContextFacade());
    }

    private long createCategory(String code, String name, Long parentId) {
        CategoryCreateRequest req = new CategoryCreateRequest();
        req.setCategoryCode(code);
        req.setCategoryName(name);
        req.setParentId(parentId);
        req.setSortNo(10);
        return service.create(req);
    }

    @Test
    void createAndGet() {
        long id = createCategory("news", "新闻", null);
        CategoryView v = service.getById(id);
        assertEquals("news", v.getCategoryCode());
        assertEquals("新闻", v.getCategoryName());
        assertEquals("ACTIVE", v.getStatus());
    }

    @Test
    void duplicateCodeRejected() {
        createCategory("news", "新闻", null);
        CategoryCreateRequest req = new CategoryCreateRequest();
        req.setCategoryCode("news");
        req.setCategoryName("新闻2");
        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(req));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void treeBuildsHierarchy() {
        long rootA = createCategory("a", "A", null);
        long rootB = createCategory("b", "B", null);
        createCategory("c", "C", rootA);
        assertEquals(3, service.list().size());
        List<CategoryNode> tree = service.tree();
        assertEquals(2, tree.size());
        CategoryNode a = tree.stream().filter(n -> n.getId().equals(rootA)).findFirst().orElseThrow();
        assertEquals(1, a.getChildren().size());
        assertEquals("C", a.getChildren().get(0).getCategoryName());
    }

    @Test
    void deleteLeafSucceedsButParentWithChildrenFails() {
        long root = createCategory("a", "A", null);
        long child = createCategory("c", "C", root);
        // 存在未删除子栏目时，删除父栏目须被拒绝
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(root));
        assertTrue(ex.getMessage().contains("子栏目"));
        // 子栏目删除后（逻辑删除），父栏目方可删除
        service.delete(child);
        assertThrows(BusinessException.class, () -> service.getById(child));
        service.delete(root);
    }

    @Test
    void buildTreePureUnit() {
        CategoryView r = new CategoryView(1L, null, "r", "根", null, 0, "ACTIVE", null, null);
        CategoryView c = new CategoryView(2L, 1L, "c", "子", null, 0, "ACTIVE", null, null);
        List<CategoryNode> tree = CmsCategoryService.buildTree(List.of(r, c));
        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).getChildren().size());
    }
}
