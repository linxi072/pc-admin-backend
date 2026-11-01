package com.acme.scaffold.integration.menu;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.entity.SysMenuDO;
import com.acme.scaffold.system.entity.SysRoleMenuDO;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V67 菜单体系按业务域重新分类集成测试。
 *
 * <p>场景：
 * <ul>
 *   <li>DB 结构：13 个新一级目录（2001–2013）均为可见 CATALOG、parent_id=0；
 *       旧目录（1002 系统管理 / 1003 工作流 / 1030 报表管理 / 1070 运维管理）置 visible=0 隐藏；</li>
 *   <li>DB 不变式：旧目录在 visible=1 且未软删的口径下不得残留任何子菜单
 *       （否则 MenuService 的祖先链补全会把隐藏目录重新拉回用户菜单树）；</li>
 *   <li>DB 结构：报表统计页 1053/1054/1055 归入「报表分析」(2012)，报表大屏 system:dashboard 归入「工作台」(2001)；</li>
 *   <li>DB 绑定：SUPER_ADMIN 绑定全部 13 个新目录；</li>
 *   <li>用户可见树（/menus/mine）：13 个新目录按 id 出现，4 个旧目录 id 不出现；
 *       其中「工作流」目录用 id=2006 定位（规避旧 id=1003 与新 id=2006 同名「工作流」歧义）；</li>
 *   <li>业务不变性：菜单迁移不改权限码，三类报表接口对超管仍全部可访问。</li>
 * </ul>
 *
 * <p>运行前提：本机 MySQL，V67 迁移随启动执行，激活 integration profile。
 */
class MenuReorganizeIT extends AbstractHttpIntegrationTest {

    /** V17 种子将 SUPER_ADMIN 绑定到所有菜单（role_id=1）。 */
    private static final long SUPER_ADMIN_ROLE_ID = 1L;

    /** V67 新增的 13 个一级目录 id（2001–2013）。 */
    private static final List<Long> NEW_CATALOG_IDS = List.of(
            2001L, 2002L, 2003L, 2004L, 2005L, 2006L, 2007L, 2008L, 2009L, 2010L, 2011L, 2012L, 2013L);

    /** V67 隐藏的旧目录 id。 */
    private static final List<Long> OLD_CATALOG_IDS = List.of(1002L, 1003L, 1030L, 1070L);

    @Autowired
    private DSLContext dsl;

    // ---------------- 1. DB 结构：13 新目录可见、4 旧目录隐藏 ----------------

    @Test
    void sysMenu_thirteenNewCatalogsVisible() {
        for (long id : NEW_CATALOG_IDS) {
            SysMenuDO m = menu(id);
            assertNotNull(m, "新目录 " + id + " 应存在");
            assertEquals(0, m.getParentId(), "新目录 " + id + " 应为一级（parent_id=0）");
            assertEquals("CATALOG", m.getMenuType(), "新目录 " + id + " 应为目录形态");
            assertEquals(1, m.getVisible(), "新目录 " + id + " 应可见（visible=1）");
            assertEquals("ACTIVE", m.getStatus(), "新目录 " + id + " 应启用");
        }
    }

    @Test
    void sysMenu_oldCatalogsHidden() {
        for (long id : OLD_CATALOG_IDS) {
            SysMenuDO m = menu(id);
            assertNotNull(m, "旧目录 " + id + " 行应仍存在");
            assertEquals("CATALOG", m.getMenuType(), "旧目录 " + id + " 应为目录形态");
            assertEquals(0, m.getVisible(), "旧目录 " + id + " 应已隐藏（visible=0）");
        }
    }

    /**
     * 不变式：旧目录在「可见且未软删」口径下不得残留子菜单。MenuService.menusForCurrentUser 的
     * 祖先链补全会把任意可见子菜单的（隐藏）父目录重新拉回用户菜单树，故 V67 必须全量重挂所有子项。
     */
    @Test
    void sysMenu_oldCatalogsHaveNoVisibleChildren() {
        int orphanCount = dsl.fetchCount(JooqTables.SYS_MENU.table(),
                JooqTables.SYS_MENU.field("parent_id", Long.class).in(OLD_CATALOG_IDS)
                        .and(JooqTables.SYS_MENU.field("visible", Integer.class).eq(1))
                        .and(JooqTables.SYS_MENU.field("deleted", Integer.class).eq(0)));
        assertEquals(0, orphanCount, "旧目录不应残留可见子菜单（V67 须全量重挂），实际残留: " + orphanCount);
    }

    // ---------------- 2. DB 结构：关键二级功能归位正确 ----------------

    @Test
    void sysMenu_reportPagesAndDashboardReparented() {
        assertChild(2012L, 1053L, 10, "report-job-exec", "/report/job-exec");
        assertChild(2012L, 1054L, 20, "report-login", "/report/login");
        assertChild(2012L, 1055L, 30, "report-api-call", "/report/api-call");
        // 报表大屏归入工作台（2001）
        SysMenuDO dashboard = menuByCode("system:dashboard");
        assertNotNull(dashboard, "报表大屏 system:dashboard 应存在");
        assertEquals(2001L, dashboard.getParentId(), "报表大屏应归入工作台(2001)");
    }

    /** 代表性业务域归位：验证重挂覆盖各新目录，避免漏挂（如 system:post 曾遗漏于 1002）。 */
    @Test
    void sysMenu_domainAssignments() {
        assertParent("system:user", 2003L);
        assertParent("system:post", 2003L);
        assertParent("system:api-resource", 2004L);
        assertParent("workflow:task", 2006L);
        assertParent("system:form-definition", 2007L);
        assertParent("system:monitor", 2008L);
        assertParent("system:tenant", 2009L);
        assertParent("system:import", 2010L);
        assertParent("system:openapi-client", 2011L);
        assertParent("job-management", 2013L);
    }

    // ---------------- 3. DB 绑定：SUPER_ADMIN 绑定 13 个新目录 ----------------

    @Test
    void roleMenu_superAdminBoundToNewCatalogs() {
        Set<Long> menuIds = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_MENU, SysRoleMenuDO.class,
                        JooqTables.SYS_ROLE_MENU.field("role_id", Long.class).eq(SUPER_ADMIN_ROLE_ID)).stream()
                .map(SysRoleMenuDO::getMenuId)
                .collect(Collectors.toSet());
        assertFalse(menuIds.isEmpty(), "SUPER_ADMIN 应绑定菜单种子");
        for (long id : NEW_CATALOG_IDS) {
            assertTrue(menuIds.contains(id), "SUPER_ADMIN 应绑定新目录 " + id);
        }
    }

    // ---------------- 4. 用户可见树（HTTP /menus/mine） ----------------

    /** 13 个新目录按 id 出现于用户菜单树。 */
    @Test
    void mine_newCatalogsAppearById() {
        List<Map<String, Object>> tree = menusMine();
        Set<String> ids = flattenIds(tree);
        for (long id : NEW_CATALOG_IDS) {
            assertTrue(ids.contains(String.valueOf(id)),
                    "用户菜单树应包含新目录 " + id + "，实际: " + ids);
        }
    }

    /** 4 个旧目录 id 不出现于用户菜单树（V67 已隐藏且子项全量迁出）。 */
    @Test
    void mine_oldCatalogsAbsent() {
        List<Map<String, Object>> tree = menusMine();
        Set<String> ids = flattenIds(tree);
        for (long id : OLD_CATALOG_IDS) {
            assertFalse(ids.contains(String.valueOf(id)),
                    "用户菜单树不应包含已隐藏旧目录 " + id + "，实际仍出现");
        }
    }

    /**
     * 「工作流」目录用 id=2006 定位（规避旧 id=1003 与新 id=2006 同名「工作流」歧义），
     * 断言其含 4 个流程子功能。
     */
    @Test
    void mine_workflowCatalogLocatedById() {
        List<Map<String, Object>> tree = menusMine();
        Map<String, Object> workflow = findById(tree, 2006L);
        assertNotNull(workflow, "应存在 id=2006 的工作流目录，实际一级目录: " + topLevelNames(tree));
        assertEquals("工作流", workflow.get("menuName"), "id=2006 的目录名应为「工作流」");

        Set<String> codes = flattenMenuCodes(childrenOf(workflow));
        assertTrue(codes.containsAll(List.of("workflow:task", "workflow:instance",
                "workflow:definition", "workflow:monitor")),
                "工作流目录应含 4 个流程子功能，实际: " + codes);
    }

    // ---------------- 5. 业务不变性：报表接口可用 ----------------

    /** 菜单迁移不改权限码 → 三类报表接口对超管仍全部 200 且返回数据。 */
    @Test
    void reportApis_stillAccessibleAfterMove() {
        String token = login("admin", "admin123");

        ResponseEntity<Result> types = get("/api/report/types", Result.class, token);
        assertEquals(200, types.getStatusCode().value());
        assertNotNull(types.getBody());
        assertTrue(types.getBody().isSuccess(), "报表类型列表应可访问");

        for (String type : List.of("JOB_EXEC", "LOGIN", "API_CALL")) {
            ResponseEntity<Result> data = get("/api/report/data?type=" + type + "&rangeType=LAST_7D",
                    Result.class, token);
            assertEquals(200, data.getStatusCode().value(), type + " 报表数据应可访问");
            assertNotNull(data.getBody(), type + " 响应体不应为 null");
            assertNotNull(data.getBody().data(), type + " 报表数据不应为 null");
        }
    }

    // ---------------- 工具方法 ----------------

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> menusMine() {
        String token = login("admin", "admin123");
        ResponseEntity<Result> resp = get("/api/system/menus/mine", Result.class, token);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), "查询我的菜单应成功");
        return (List<Map<String, Object>>) resp.getBody().data();
    }

    /** 递归收集树中所有节点的 id（顶层 + 全部子孙），统一字符串便于与 JSON 字符串 id 比较。 */
    private Set<String> flattenIds(List<Map<String, Object>> nodes) {
        Set<String> ids = new HashSet<>();
        for (Map<String, Object> n : nodes) {
            ids.add(String.valueOf(n.get("id")));
            ids.addAll(flattenIds(childrenOf(n)));
        }
        return ids;
    }

    /** 递归收集树中所有节点的 menuCode。 */
    private Set<String> flattenMenuCodes(List<Map<String, Object>> nodes) {
        Set<String> codes = new HashSet<>();
        for (Map<String, Object> n : nodes) {
            if (n.get("menuCode") != null) {
                codes.add(String.valueOf(n.get("menuCode")));
            }
            codes.addAll(flattenMenuCodes(childrenOf(n)));
        }
        return codes;
    }

    /** 按 id 递归查找节点（id 为雪花 ID，JSON 序列化为字符串）。 */
    private Map<String, Object> findById(List<Map<String, Object>> nodes, long id) {
        for (Map<String, Object> n : nodes) {
            if (String.valueOf(id).equals(String.valueOf(n.get("id")))) {
                return n;
            }
            Map<String, Object> found = findById(childrenOf(n), id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private List<String> topLevelNames(List<Map<String, Object>> tree) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> m : tree) {
            if ("0".equals(String.valueOf(m.get("parentId")))) {
                names.add(String.valueOf(m.get("menuName")));
            }
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> childrenOf(Map<String, Object> node) {
        Object children = node.get("children");
        return children instanceof List ? (List<Map<String, Object>>) children : List.of();
    }

    private SysMenuDO menu(long id) {
        return JooqWriters.fetchOne(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                JooqTables.SYS_MENU.field("id", Long.class).eq(id));
    }

    private SysMenuDO menuByCode(String menuCode) {
        return JooqWriters.fetchOne(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                JooqTables.SYS_MENU.field("menu_code", String.class).eq(menuCode));
    }

    private void assertParent(String menuCode, long parentId) {
        SysMenuDO m = menuByCode(menuCode);
        assertNotNull(m, "菜单 " + menuCode + " 应存在");
        assertEquals(parentId, m.getParentId(), menuCode + " 应归入目录 " + parentId);
        assertEquals(0, m.getDeleted(), menuCode + " 应未软删");
    }

    private void assertChild(long parentId, long id, int sortNo, String menuCode, String routePath) {
        SysMenuDO m = menu(id);
        assertNotNull(m, "菜单 " + id + " 应存在");
        assertEquals(parentId, m.getParentId(), menuCode + " 的父菜单应为 " + parentId);
        assertEquals(sortNo, m.getSortNo(), menuCode + " 的 sort_no 应为 " + sortNo);
        assertEquals(menuCode, m.getMenuCode());
        assertEquals(routePath, m.getRoutePath(), "路由 path 不应变化");
        assertEquals("report:read", m.getPermissionCode(), "权限码不应变化");
        assertEquals(0, m.getDeleted(), menuCode + " 应处于未删除状态");
    }
}
