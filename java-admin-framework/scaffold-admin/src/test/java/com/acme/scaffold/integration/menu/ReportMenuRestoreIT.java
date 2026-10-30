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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * V31 菜单重组集成测试：恢复「报表管理」一级菜单，迁入定时任务执行 / 登录 / 接口调用统计。
 *
 * <p>场景：
 * <ul>
 *   <li>menus/mine：报表管理为一级目录且含 3 个历史统计页（任务执行 → 登录 → 接口调用）+ M27 报表大屏
 *       （system:dashboard，V52 种子挂在 1030 下，路由 /report/dashboard），共 4 个子项，权限码与路由 path 保持不变；</li>
 *   <li>menus/mine：运维管理不再包含报表统计，仅保留功能菜单（V31 时 7 个，
 *       V33 文件管理模块迁入后为 8 个）；</li>
 *   <li>sys_menu 结构：1030 复活为一级 CATALOG（sort 45，接排个人中心与运维管理之间），
 *       1053/1054/1055 迁入并重排 sort 10/20/30，1050~1052 公共统计页保持软删不复活；</li>
 *   <li>sys_role_menu：SUPER_ADMIN 重新绑定报表管理目录（V30 清理过），3 个统计页绑定不丢失；</li>
 *   <li>业务不变性：菜单迁移不改权限码，三类报表接口对超管仍全部可访问。</li>
 * </ul>
 *
 * <p>运行前提：本机 MySQL（root/root），V31 迁移已随启动执行，激活 integration profile。
 */
class ReportMenuRestoreIT extends AbstractHttpIntegrationTest {

    /** V17 种子将 SUPER_ADMIN 绑定到所有菜单（role_id=1）。 */
    private static final long SUPER_ADMIN_ROLE_ID = 1L;

    private static final long REPORT_MENU_ID = 1030L;
    private static final long OPS_MENU_ID = 1070L;

    @Autowired
    private DSLContext dsl;

    // ---------------- 1. 菜单树（用户可见视角） ----------------

    /**
     * 报表管理为一级目录，含 3 个历史统计页（任务执行 → 登录 → 接口调用）+ M27 报表大屏
     * （system:dashboard，V52 种子挂在 1030 下），共 4 个子项；权限码与路由 path 保持不变。
     */
    @Test
    void mine_reportMenuTopLevelWithReportPagesAndDashboard() {
        List<Map<String, Object>> tree = menusMine();

        Map<String, Object> report = topLevel(tree, "报表管理");
        assertNotNull(report, "应存在一级菜单「报表管理」，实际一级菜单: " + topLevelNames(tree));

        List<Map<String, Object>> children = childrenOf(report);
        assertEquals(4, children.size(), "报表管理应含 3 个历史统计页 + M27 报表大屏，实际: " + codes(children));
        assertEquals("report-job-exec", children.get(0).get("menuCode"));
        assertEquals("report-login", children.get(1).get("menuCode"));
        assertEquals("report-api-call", children.get(2).get("menuCode"));
        assertEquals("system:dashboard", children.get(3).get("menuCode"));

        // 3 个历史统计页权限码与路由保持不变（授权与业务不变性）
        for (Map<String, Object> c : children.subList(0, 3)) {
            assertEquals("report:read", c.get("permissionCode"), "历史统计页权限码应仍为 report:read");
        }
        assertEquals("system:dashboard:read", children.get(3).get("permissionCode"), "报表大屏权限码应为 system:dashboard:read");
        assertEquals("/report/job-exec", children.get(0).get("routePath"));
        assertEquals("/report/login", children.get(1).get("routePath"));
        assertEquals("/report/api-call", children.get(2).get("routePath"));
        assertEquals("/report/dashboard", children.get(3).get("routePath"));
    }

    /**
     * 运维管理不再包含 3 个统计页，仅保留功能菜单。
     *
     * <p>V31 重组时为 7 个；V33 文件管理模块在运维管理下新增 1080「文件管理」，
     * 故当前基线为 8 个。本断言锁定「统计页已迁出」这一重组语义，功能菜单集合
     * 随业务模块演进，新增模块时需同步维护此清单。</p>
     */
    @Test
    void mine_opsMenuKeepsOnlySevenFunctions() {
        List<Map<String, Object>> tree = menusMine();

        Map<String, Object> ops = topLevel(tree, "运维管理");
        assertNotNull(ops, "应存在一级菜单「运维管理」");

        assertEquals(List.of("system:api-resource", "dev-codegen", "system:monitor",
                        "system:cache", "system:datasource", "system:login-log", "system:operation-log",
                        "file-management"),
                codes(childrenOf(ops)),
                "运维管理不应再含报表统计页（已迁入报表管理），功能菜单为既有 7 项 + V33 文件管理");
    }

    // ---------------- 2. 库结构（DSL 直查） ----------------

    /** 1030 复活为一级 CATALOG；3 个统计页迁入并重排 sort；1050~1052 保持软删。 */
    @Test
    void sysMenu_structureMatchesV31() {
        SysMenuDO report = menu(REPORT_MENU_ID);
        assertNotNull(report, "报表管理(1030) 行应存在");
        assertEquals(0, report.getDeleted(), "报表管理应已复活（deleted=0）");
        assertEquals(0L, report.getParentId(), "报表管理应为一级菜单（parent_id=0）");
        assertEquals("CATALOG", report.getMenuType(), "报表管理应为目录形态");
        assertNull(report.getRoutePath(), "目录不导航，route_path 应为 NULL");
        assertNull(report.getPermissionCode(), "目录不鉴权，permission_code 应为 NULL");
        assertEquals(45, report.getSortNo(), "应接排个人中心(40)之后、运维管理(50)之前");

        assertChild(REPORT_MENU_ID, 1053L, 10, "report-job-exec", "/report/job-exec");
        assertChild(REPORT_MENU_ID, 1054L, 20, "report-login", "/report/login");
        assertChild(REPORT_MENU_ID, 1055L, 30, "report-api-call", "/report/api-call");

        for (long id : new long[]{1050L, 1051L, 1052L}) {
            SysMenuDO removed = menu(id);
            assertNotNull(removed, "公共统计页 " + id + " 行应仍在（软删）");
            assertEquals(1, removed.getDeleted(), "公共统计页 " + id + " 应保持软删，不得复活");
        }

        SysMenuDO ops = menu(OPS_MENU_ID);
        assertNotNull(ops, "运维管理(1070) 应存在");
        assertEquals(0, ops.getDeleted());
        assertEquals(0L, ops.getParentId(), "运维管理应仍为一级菜单");
        assertEquals(50, ops.getSortNo(), "运维管理 sort_no 不应变化");
    }

    /** SUPER_ADMIN 重新绑定报表管理目录（V30 清理过，V31 补回），3 个统计页绑定不丢失。 */
    @Test
    void roleMenu_superAdminReboundToReportCatalog() {
        List<Long> menuIds = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_MENU, SysRoleMenuDO.class,
                        JooqTables.SYS_ROLE_MENU.field("role_id", Long.class).eq(SUPER_ADMIN_ROLE_ID)).stream()
                .map(SysRoleMenuDO::getMenuId)
                .collect(Collectors.toList());
        assertFalse(menuIds.isEmpty(), "SUPER_ADMIN 应绑定菜单种子");
        assertTrue(menuIds.contains(REPORT_MENU_ID), "SUPER_ADMIN 应重新绑定报表管理目录");
        assertTrue(menuIds.contains(1053L) && menuIds.contains(1054L) && menuIds.contains(1055L),
                "SUPER_ADMIN 应仍绑定 3 个统计页（授权不丢失）");
    }

    // ---------------- 3. 业务不变性：报表接口可用 ----------------

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

    /** 一级节点（parentId 以字符串 "0" 下发——雪花 ID 统一 ToStringSerializer）。 */
    private Map<String, Object> topLevel(List<Map<String, Object>> tree, String menuName) {
        return tree.stream()
                .filter(m -> "0".equals(String.valueOf(m.get("parentId"))))
                .filter(m -> menuName.equals(m.get("menuName")))
                .findFirst()
                .orElse(null);
    }

    private List<String> topLevelNames(List<Map<String, Object>> tree) {
        return tree.stream().map(m -> String.valueOf(m.get("menuName"))).collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> childrenOf(Map<String, Object> node) {
        Object children = node.get("children");
        return children instanceof List ? (List<Map<String, Object>>) children : List.of();
    }

    private List<String> codes(List<Map<String, Object>> nodes) {
        return nodes.stream().map(m -> String.valueOf(m.get("menuCode"))).collect(Collectors.toList());
    }

    private SysMenuDO menu(long id) {
        return JooqWriters.fetchOne(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                JooqTables.SYS_MENU.field("id", Long.class).eq(id));
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
