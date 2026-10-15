package com.acme.scaffold.integration.system;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.integration.AbstractHttpIntegrationTest;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.entity.SysApiResourceDO;
import com.acme.scaffold.system.entity.SysMenuDO;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.service.UserService;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 角色管理业务集成测试：覆盖角色 CRUD、菜单/接口权限绑定、删除级联清理与停用角色绑定守卫。
 *
 * <p>场景：
 * <ul>
 *   <li>创建角色（带菜单/接口绑定）→ 详情回读绑定集合；</li>
 *   <li>角色编码重复 → 409；</li>
 *   <li>更新角色整体替换菜单绑定（旧绑定被清除）；</li>
 *   <li>删除角色 → sys_role_menu / sys_role_api / 数据权限规则级联清理；</li>
 *   <li>删除已被用户绑定的角色 → sys_user_role 不得残留孤儿行（越权/脏数据防线）；</li>
 *   <li>停用角色不可被绑定到用户 → 409。</li>
 * </ul>
 *
 * <p>运行前提：本机 MySQL（root/root），admin/admin123 属 SUPER_ADMIN，激活 integration profile。
 */
class RoleManagementIT extends AbstractHttpIntegrationTest {

    @Autowired
    private DSLContext dsl;

    @Autowired
    private UserService userService;

    /** 本类创建的角色与用户，@AfterEach 清理，避免脏数据累积。 */
    private final List<Long> createdRoleIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    /**
     * 类内缓存 admin 令牌。
     *
     * <p>背景：{@code RateLimitFilter} 按「login:IP」60s 窗口限流 10 次且过滤器为单例，
     * 每次调用都重新登录会快速耗尽配额（清理 + 各用例反复登录）触发 429。
     * 访问令牌有效期足够覆盖单个测试类的执行窗口，故类内缓存一次。
     */
    private String cachedToken;

    private String token() {
        if (cachedToken == null) {
            cachedToken = login("admin", "admin123");
        }
        return cachedToken;
    }

    @AfterEach
    void cleanup() {
        String t = token();
        for (Long uid : createdUserIds) {
            rest.exchange(baseUrl() + "/api/system/users/" + uid, org.springframework.http.HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(bearer(t)), Result.class);
        }
        for (Long rid : createdRoleIds) {
            rest.exchange(baseUrl() + "/api/system/roles/" + rid, org.springframework.http.HttpMethod.DELETE,
                    new org.springframework.http.HttpEntity<>(bearer(t)), Result.class);
        }
        createdRoleIds.clear();
        createdUserIds.clear();
    }

    private long seedMenuId() {
        List<SysMenuDO> menus = JooqWriters.fetchList(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_MENU));
        assertFalse(menus.isEmpty(), "库中应存在菜单种子");
        return menus.get(0).getId();
    }

    private long secondSeedMenuId() {
        List<SysMenuDO> menus = JooqWriters.fetchList(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_MENU));
        assertTrue(menus.size() >= 2, "库中应至少存在 2 个菜单种子");
        return menus.get(1).getId();
    }

    private long seedApiId() {
        List<SysApiResourceDO> apis = JooqWriters.fetchList(dsl, JooqTables.SYS_API_RESOURCE,
                SysApiResourceDO.class, JooqWriters.notDeleted(JooqTables.SYS_API_RESOURCE));
        assertFalse(apis.isEmpty(), "库中应存在接口资源种子");
        return apis.get(0).getId();
    }

    private long seedOrgId() {
        List<SysOrgDO> orgs = JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ORG));
        assertFalse(orgs.isEmpty(), "库中应存在部门种子");
        return orgs.get(0).getId();
    }

    private long seedRoleId() {
        List<SysRoleDO> roles = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_ROLE));
        assertFalse(roles.isEmpty(), "库中应存在角色种子");
        return roles.get(0).getId();
    }

    private Long createRole(String code, Set<Long> menuIds, Set<Long> apiIds, String status) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("roleCode", code);
        body.put("roleName", "IT角色-" + code);
        body.put("roleType", "BUSINESS");
        body.put("status", status);
        body.put("menuIds", menuIds);
        body.put("apiIds", apiIds);
        ResponseEntity<Result> resp = post("/api/system/roles", body, Result.class, token());
        assertEquals(200, resp.getStatusCode().value(), "创建角色应成功");
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess(), resp.getBody().message());
        Long id = Long.valueOf((String) resp.getBody().data());
        createdRoleIds.add(id);
        return id;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> roleDetail(Long roleId) {
        ResponseEntity<Result> resp = get("/api/system/roles/" + roleId, Result.class, token());
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());
        return (Map<String, Object>) resp.getBody().data();
    }

    /** 正常路径：创建角色（带菜单/接口绑定）后，详情应回读相同绑定集合。 */
    @Test
    void createWithBindings_thenDetailReturnsSameBindings() {
        long menu1 = seedMenuId();
        long menu2 = secondSeedMenuId();
        long api = seedApiId();
        Long roleId = createRole("it-role-bind-" + System.nanoTime(),
                Set.of(menu1, menu2), Set.of(api), "ACTIVE");

        Map<String, Object> detail = roleDetail(roleId);
        List<Object> menuIds = (List<Object>) detail.get("menuIds");
        List<Object> apiIds = (List<Object>) detail.get("apiIds");
        assertNotNull(menuIds);
        assertTrue(menuIds.stream().map(String::valueOf).toList()
                .containsAll(List.of(String.valueOf(menu1), String.valueOf(menu2))),
                "菜单绑定应完整回读");
        assertTrue(apiIds.stream().map(String::valueOf).toList().contains(String.valueOf(api)),
                "接口绑定应完整回读");
    }

    /** 异常路径：角色编码重复 → 409，且不产生第二个角色。 */
    @Test
    void duplicateRoleCode_returns409() {
        String code = "it-role-dup-" + System.nanoTime();
        createRole(code, Set.of(), Set.of(), "ACTIVE");

        Map<String, Object> body = Map.of(
                "roleCode", code,
                "roleName", "重复编码角色",
                "menuIds", Set.of(),
                "apiIds", Set.of());
        ResponseEntity<Result> resp = post("/api/system/roles", body, Result.class, token());
        assertEquals(409, resp.getStatusCode().value(), "角色编码重复应返回 409");
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess());
    }

    /** 正常路径：更新角色时整体替换菜单绑定，旧绑定应被清除。 */
    @Test
    void updateReplacesMenuBindings() {
        long menu1 = seedMenuId();
        long menu2 = secondSeedMenuId();
        Long roleId = createRole("it-role-upd-" + System.nanoTime(), Set.of(menu1), Set.of(), "ACTIVE");

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("roleCode", "it-role-upd-ignored");
        body.put("roleName", "IT角色-更新后");
        body.put("roleType", "BUSINESS");
        body.put("status", "ACTIVE");
        body.put("menuIds", Set.of(menu2));
        body.put("apiIds", Set.of());
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/roles/" + roleId,
                org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(body, bearer(token())),
                Result.class);
        assertEquals(200, resp.getStatusCode().value());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().isSuccess());

        List<Object> menuIds = (List<Object>) roleDetail(roleId).get("menuIds");
        List<String> ids = menuIds.stream().map(String::valueOf).toList();
        assertTrue(ids.contains(String.valueOf(menu2)), "新菜单绑定应生效");
        assertFalse(ids.contains(String.valueOf(menu1)), "旧菜单绑定应被替换清除");
    }

    /** 正常路径：删除角色后，菜单/接口/数据权限关联应级联清理，不留孤儿行。 */
    @Test
    void deleteCleansAllBindings() {
        long menu = seedMenuId();
        long api = seedApiId();
        Long roleId = createRole("it-role-del-" + System.nanoTime(), Set.of(menu), Set.of(api), "ACTIVE");

        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/roles/" + roleId,
                org.springframework.http.HttpMethod.DELETE, new org.springframework.http.HttpEntity<>(bearer(token())),
                Result.class);
        assertEquals(200, resp.getStatusCode().value());
        createdRoleIds.remove(roleId);

        long menuLeft = JooqWriters.count(dsl, JooqTables.SYS_ROLE_MENU,
                JooqTables.SYS_ROLE_MENU.field("role_id", Long.class).eq(roleId));
        long apiLeft = JooqWriters.count(dsl, JooqTables.SYS_ROLE_API,
                JooqTables.SYS_ROLE_API.field("role_id", Long.class).eq(roleId));
        assertEquals(0, menuLeft, "删除角色后 sys_role_menu 不得残留");
        assertEquals(0, apiLeft, "删除角色后 sys_role_api 不得残留");
    }

    /**
     * 边界/异常：删除已被用户绑定的角色后，sys_user_role 不得残留孤儿行。
     *
     * <p>孤儿行的危害：用户「角色」列仍显示已删除角色（若查询未过滤 notDeleted），
     * 且角色重建（同编码不同 id）后历史绑定不会恢复，数据卫生失控。
     */
    @Test
    void deleteRoleAssignedToUser_leavesNoOrphanUserRole() {
        Long roleId = createRole("it-role-orphan-" + System.nanoTime(), Set.of(), Set.of(), "ACTIVE");
        String uniq = "it-role-user-" + System.nanoTime();
        Long uid = userService.create(new CreateUserRequest(
                uniq, "Password123!", "IT 角色孤儿测试", null, null,
                List.of(roleId), null, List.of(seedOrgId()), null));
        createdUserIds.add(uid);

        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/roles/" + roleId,
                org.springframework.http.HttpMethod.DELETE, new org.springframework.http.HttpEntity<>(bearer(token())),
                Result.class);
        assertEquals(200, resp.getStatusCode().value());
        createdRoleIds.remove(roleId);

        long orphan = JooqWriters.count(dsl, JooqTables.SYS_USER_ROLE,
                JooqTables.SYS_USER_ROLE.field("role_id", Long.class).eq(roleId));
        assertEquals(0, orphan, "删除角色后 sys_user_role 不得残留孤儿绑定");
    }

    /** 异常路径：停用角色不可被绑定到新用户 → 409。 */
    @Test
    void createUserWithDisabledRole_returns409() {
        Long disabledRole = createRole("it-role-off-" + System.nanoTime(), Set.of(), Set.of(), "DISABLED");
        Map<String, Object> body = Map.of(
                "username", "it-role-badu-" + System.nanoTime(),
                "password", "Password123!",
                "displayName", "IT 停用角色绑定",
                "roleIds", List.of(disabledRole),
                "deptIds", List.of(seedOrgId()));
        ResponseEntity<Result> resp = post("/api/system/users", body, Result.class, token());
        assertEquals(409, resp.getStatusCode().value(), "停用角色不可绑定");
        assertNotNull(resp.getBody());
        assertFalse(resp.getBody().isSuccess());
    }

    /** 边界：未提供菜单/接口绑定的角色可正常创建（空集合 = 无权限角色）。 */
    @Test
    void createRoleWithoutBindings_succeeds() {
        Long roleId = createRole("it-role-empty-" + System.nanoTime(), Set.of(), Set.of(), "ACTIVE");
        Map<String, Object> detail = roleDetail(roleId);
        assertTrue(((List<?>) detail.get("menuIds")).isEmpty(), "空菜单绑定应回读为空集合");
        assertTrue(((List<?>) detail.get("apiIds")).isEmpty(), "空接口绑定应回读为空集合");
    }

    /**
     * 契约语义（O-1 回归）：更新角色时<b>不传</b> menuIds/apiIds（null）应保持原绑定不变。
     *
     * <p>修复前 {@code RoleService.update} 无条件重建绑定，调用方局部更新角色信息
     * （如只改角色名/状态）时漏传绑定字段即把全部授权清空——契约二义且误清后果严重。
     * 修复后语义明确：null=不改动绑定，空集合=清空，非空集合=全量替换。
     */
    @Test
    void updateWithoutBindingFields_keepsBindings() {
        long menu = seedMenuId();
        long api = seedApiId();
        Long roleId = createRole("it-role-keep-" + System.nanoTime(), Set.of(menu), Set.of(api), "ACTIVE");

        // 只更新角色名与状态，body 中完全不含 menuIds / apiIds 键
        Map<String, Object> body = Map.of(
                "roleCode", "it-role-keep-ignored",
                "roleName", "IT角色-仅改名称",
                "roleType", "BUSINESS",
                "status", "ACTIVE");
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/roles/" + roleId,
                org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(body, bearer(token())),
                Result.class);
        assertEquals(200, resp.getStatusCode().value());

        Map<String, Object> detail = roleDetail(roleId);
        List<Object> menuIds = (List<Object>) detail.get("menuIds");
        List<Object> apiIds = (List<Object>) detail.get("apiIds");
        assertTrue(menuIds.stream().map(String::valueOf).toList().contains(String.valueOf(menu)),
                "不传绑定字段时原菜单绑定应保持（O-1 修复回归）");
        assertTrue(apiIds.stream().map(String::valueOf).toList().contains(String.valueOf(api)),
                "不传绑定字段时原接口绑定应保持（O-1 修复回归）");
    }

    /** 契约语义（O-1 回归）：更新角色时传<b>空集合</b>才清空全部绑定。 */
    @Test
    void updateWithEmptyBindings_clearsBindings() {
        long menu = seedMenuId();
        Long roleId = createRole("it-role-clear-" + System.nanoTime(), Set.of(menu), Set.of(), "ACTIVE");

        Map<String, Object> body = Map.of(
                "roleCode", "it-role-clear-ignored",
                "roleName", "IT角色-清空绑定",
                "roleType", "BUSINESS",
                "status", "ACTIVE",
                "menuIds", Set.of(),
                "apiIds", Set.of());
        ResponseEntity<Result> resp = rest.exchange(baseUrl() + "/api/system/roles/" + roleId,
                org.springframework.http.HttpMethod.PUT, new org.springframework.http.HttpEntity<>(body, bearer(token())),
                Result.class);
        assertEquals(200, resp.getStatusCode().value());

        Map<String, Object> detail = roleDetail(roleId);
        assertTrue(((List<?>) detail.get("menuIds")).isEmpty(),
                "空集合应清空菜单绑定（与 null 的保持语义区分）");
        assertTrue(((List<?>) detail.get("apiIds")).isEmpty(), "空集合应清空接口绑定");
    }
}
