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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 菜单动态加载集成测试：验证「登录用户 → 按角色解析可见菜单 → 补全祖先链构建树」全链路，
 * 以及「未授权菜单绝不返回」的数据一致性契约。
 *
 * <p>运行前提（本机 / CI，不使用 Docker）：
 * <ol>
 *   <li>原生 MySQL 8.x，库名 java_admin，Flyway 自动建表并注入 V17 种子（SUPER_ADMIN=role_id=1 绑定全部菜单）；</li>
 *   <li>admin/admin123 账号存在且属于 SUPER_ADMIN；</li>
 *   <li>启动带 {@code -Dio.netty.resolver.dns.useJdkResolver=true} 直连 IPv6 回环。</li>
 * </ol>
 *
 * 运行：{@code mvn -o test -Dtest=MenuIntegrationIT -Dspring.profiles.active=integration
 *   -DTEST_DB_URL=... -DTEST_DB_USER=... -DTEST_DB_PASSWORD=...}
 */
class MenuIntegrationIT extends AbstractHttpIntegrationTest {

    /** V17 种子将 SUPER_ADMIN 绑定到所有菜单（role_id=1）。 */
    private static final long SUPER_ADMIN_ROLE_ID = 1L;

    @Autowired
    private DSLContext dsl;

    /** 正常路径：认证管理员可取得按角色过滤的菜单树（含父子嵌套）。 */
    @Test
    void mineReturnsTreeForAuthenticatedAdmin() {
        String token = login("admin", "admin123");
        ResponseEntity<Result> resp = get("/api/system/menus/mine", Result.class, token);
        assertEquals(200, resp.getStatusCode().value());
        Result r = resp.getBody();
        assertNotNull(r);
        assertTrue(r.isSuccess());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> menus = (List<Map<String, Object>>) r.data();
        assertNotNull(menus);
        assertFalse(menus.isEmpty(), "SUPER_ADMIN 应至少看到菜单");

        boolean hasSubMenu = menus.stream().anyMatch(m -> {
            Object children = m.get("children");
            return children instanceof List && !((List<?>) children).isEmpty();
        });
        assertTrue(hasSubMenu, "菜单应呈现为父子树结构（至少存在一个含子节点的目录）");
    }

    /** 异常边界：未携带令牌访问受保护接口应返回 401。 */
    @Test
    void mineUnauthorizedReturns401() {
        ResponseEntity<Result> resp = get("/api/system/menus/mine", Result.class, null);
        assertEquals(401, resp.getStatusCode().value());
    }

    /**
     * 数据一致性：返回菜单必须是「授权菜单及其全部祖先」的闭包子集，
     * 即任何一个返回项都不得超出当前角色授权范围（无越权）。
     */
    @Test
    void mineOnlyReturnsAuthorizedOrAncestorMenus() {
        String token = login("admin", "admin123");
        ResponseEntity<Result> resp = get("/api/system/menus/mine", Result.class, token);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> menus = (List<Map<String, Object>>) resp.getBody().data();
        Set<Long> returnedIds = new HashSet<>();
        for (Map<String, Object> m : menus) {
            // 雪花 ID（18 位）经 @JsonSerialize(ToStringSerializer) 以字符串下发，避免 JS Number 精度丢失
            returnedIds.add(Long.parseLong(m.get("id").toString()));
        }

        // 角色绑定的菜单
        List<SysRoleMenuDO> bindings = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_MENU, SysRoleMenuDO.class,
                JooqTables.SYS_ROLE_MENU.field("role_id", Long.class).eq(SUPER_ADMIN_ROLE_ID));
        Set<Long> authorizedMenuIds = new HashSet<>();
        bindings.forEach(b -> authorizedMenuIds.add(b.getMenuId()));

        // 全量菜单，构建 parent 链用于向上追溯祖先
        List<SysMenuDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_MENU));
        Map<Long, Long> parentOf = new java.util.HashMap<>();
        for (SysMenuDO m : all) {
            parentOf.put(m.getId(), m.getParentId() == null ? 0L : m.getParentId());
        }

        // 授权菜单 + 它们各自的祖先链 → 闭包
        Set<Long> closure = new HashSet<>(authorizedMenuIds);
        for (Long id : new HashSet<>(authorizedMenuIds)) {
            Long pid = parentOf.get(id);
            while (pid != null && pid != 0L && closure.add(pid)) {
                pid = parentOf.get(pid);
            }
        }

        for (Long id : returnedIds) {
            assertTrue(closure.contains(id),
                    "菜单 id=" + id + " 不应超出当前角色授权（含祖先）范围");
        }
    }

    /**
     * 数据一致性（O-2 回归）：删除菜单后 sys_role_menu 不得残留孤儿行（按 menu_id 级联清理）。
     *
     * <p>孤儿行的危害：菜单已删但角色仍"挂"着它——{@code menusForCurrentUser} 的授权
     * 闭包计算会包含不存在的菜单 id（被 byId 过滤后不渲染，但数据卫生失控），
     * 且重建同 id 菜单时历史授权不会恢复。修复前 {@code MenuService.delete}
     * 只删主表不碰关联表；修复后与 {@code RoleService.delete} 的级联清理互为补充
     * （那里按 role_id 清，这里按 menu_id 清）。
     */
    @Test
    void deleteMenu_leavesNoOrphanRoleMenu() {
        String token = login("admin", "admin123");

        // 造一个无子菜单的独立菜单（满足删除前置条件）
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("menuCode", "it-menu-orphan-" + System.nanoTime());
        body.put("menuName", "IT孤儿菜单");
        body.put("menuType", "MENU");
        ResponseEntity<Result> created = post("/api/system/menus", body, Result.class, token);
        assertEquals(200, created.getStatusCode().value(), "创建菜单应成功");
        Long menuId = Long.valueOf((String) created.getBody().data());

        // 直接给 SUPER_ADMIN 绑定该菜单，模拟既有授权
        SysRoleMenuDO rm = new SysRoleMenuDO();
        rm.setRoleId(SUPER_ADMIN_ROLE_ID);
        rm.setMenuId(menuId);
        JooqWriters.insert(dsl, JooqTables.SYS_ROLE_MENU, rm);
        assertEquals(1, JooqWriters.count(dsl, JooqTables.SYS_ROLE_MENU,
                JooqTables.SYS_ROLE_MENU.field("menu_id", Long.class).eq(menuId)),
                "前置绑定应存在");

        // 删除菜单（逻辑删）
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/menus/" + menuId,
                HttpMethod.DELETE, new HttpEntity<>(bearer(token)), Result.class);
        assertEquals(200, resp.getStatusCode().value(), "删除菜单应成功");

        long orphan = JooqWriters.count(dsl, JooqTables.SYS_ROLE_MENU,
                JooqTables.SYS_ROLE_MENU.field("menu_id", Long.class).eq(menuId));
        assertEquals(0, orphan, "删除菜单后 sys_role_menu 不得残留孤儿行（O-2 修复回归）");
    }
}
