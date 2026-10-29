package com.acme.scaffold.system.service;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.password.PasswordHistoryService;
import com.acme.scaffold.security.permission.ColumnPermissionResourceRegistry;
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.dto.CreateMenuRequest;
import com.acme.scaffold.system.dto.CreateRoleRequest;
import com.acme.scaffold.system.dto.MenuSortItem;
import com.acme.scaffold.system.dto.MenuTreeVO;
import com.acme.scaffold.system.dto.UpdateMenuRequest;
import com.acme.scaffold.system.dto.UpdateUserRequest;
import com.acme.scaffold.system.entity.SysMenuDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysRoleMenuDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import org.jooq.DSLContext;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * 菜单缓存行为测试（M5 续：{@link MenuService} 两棵读树纳入 Spring Cache，
 * 跨服务驱逐覆盖 {@link RoleService} / {@link UserService}）。
 *
 * <p>沿用字典缓存试点的同一范式（见 {@code DictDataServiceCacheTest}）：轻量 Spring 上下文
 * （{@code @EnableCaching} + ConcurrentMapCacheManager）驱动真实缓存代理，
 * {@code mockStatic} 截断 jOOQ 静态工具作为打库替身；不核验打库调用次数
 * （项目约定：DB 交互路径由 MySQL 集成测试覆盖，mock 链式核验脆弱），
 * 而是通过「替身按次返回不同值 + {@link CacheManager} 可观测状态」断言：
 * <ul>
 *   <li>命中：二次读取仍是首次结果（替身第二次会返回不同值）；</li>
 *   <li>按用户分键：不同用户 id 各自独立缓存项；</li>
 *   <li>驱逐：写操作后对应缓存项消失（回源），且跨服务驱逐互不遗漏；</li>
 *   <li>精确性：用户角色变更只驱逐该用户一项，不影响其他用户。</li>
 * </ul>
 * 不依赖数据库与 Redis，离线可跑。
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = MenuServiceCacheTest.TestConfig.class)
class MenuServiceCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }

        @Bean
        MenuService menuService() {
            return new MenuService(mock(DSLContext.class));
        }

        @Bean
        RoleService roleService() {
            return new RoleService(mock(DSLContext.class));
        }

        @Bean
        UserService userService() {
            return new UserService(mock(DSLContext.class), mock(PasswordEncoder.class),
                    mock(TokenVersionService.class), mock(PasswordHistoryService.class),
                    mock(PostService.class), mock(RoleColumnPermissionService.class),
                    mock(ColumnPermissionResourceRegistry.class), mock(SecurityContextFacade.class));
        }
    }

    @Autowired
    private MenuService menuService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserService userService;

    @Autowired
    private CacheManager cacheManager;

    /**
     * 用例间缓存隔离：同一 Spring 上下文内所有用例共享同一个 CacheManager，
     * 不清空会出现「上一个用例的缓存残留命中」，导致断言错位。
     */
    @BeforeEach
    void clearCachesBetweenTests() {
        clearCache(MenuService.MENU_TREE_CACHE);
        clearCache(MenuService.USER_MENUS_CACHE);
    }

    private void clearCache(String name) {
        Cache cache = cacheManager.getCache(name);
        if (cache != null) {
            cache.clear();
        }
    }

    private static SysMenuDO menu(Long id, Long parentId, String name, int sortNo) {
        SysMenuDO m = new SysMenuDO();
        m.setId(id);
        m.setParentId(parentId);
        m.setMenuCode("menu-" + id);
        m.setMenuName(name);
        m.setMenuType("MENU");
        m.setVisible(1);
        m.setSortNo(sortNo);
        m.setStatus("ACTIVE");
        return m;
    }

    private static SysUserRoleDO userRole(Long userId, Long roleId) {
        SysUserRoleDO ur = new SysUserRoleDO();
        ur.setUserId(userId);
        ur.setRoleId(roleId);
        ur.setIsPrimary(1);
        return ur;
    }

    private static SysRoleMenuDO roleMenu(Long roleId, Long menuId) {
        SysRoleMenuDO rm = new SysRoleMenuDO();
        rm.setRoleId(roleId);
        rm.setMenuId(menuId);
        return rm;
    }

    private static SysRoleDO role(Long id) {
        SysRoleDO r = new SysRoleDO();
        r.setId(id);
        r.setRoleCode("ROLE-" + id);
        r.setRoleName("角色" + id);
        r.setStatus("ACTIVE");
        return r;
    }

    private static SysUserDO user(Long id) {
        SysUserDO u = new SysUserDO();
        u.setId(id);
        u.setUsername("user-" + id);
        u.setStatus("ACTIVE");
        return u;
    }

    private void stubCommonReads(MockedStatic<JooqWriters> writers) {
        writers.when(() -> JooqWriters.notDeleted(any())).thenReturn(DSL.trueCondition());
    }

    /** tree() 读替身（走可变参排序重载）：第 N 次打库返回名字为「第N次」的根菜单。 */
    private void stubTreeChanging(MockedStatic<JooqWriters> writers) {
        AtomicInteger calls = new AtomicInteger();
        stubCommonReads(writers);
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysMenuDO.class), any(),
                        any(OrderField[].class)))
                .thenAnswer(inv -> {
                    int n = calls.incrementAndGet();
                    return List.of(menu(1L, 0L, "第" + n + "次", 1));
                });
    }

    /**
     * menusForCurrentUser() 读替身（走 4 参无排序重载）：第 N 次打库时
     * 子树菜单名为「第N次」。userRole / roleMenu 桩与用户参数无关（按类匹配）。
     */
    private void stubUserMenusChanging(MockedStatic<JooqWriters> writers) {
        AtomicInteger calls = new AtomicInteger();
        stubCommonReads(writers);
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysUserRoleDO.class), any()))
                .thenReturn(List.of(userRole(1L, 100L)));
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysRoleMenuDO.class), any()))
                .thenReturn(List.of(roleMenu(100L, 10L)));
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysMenuDO.class), any()))
                .thenAnswer(inv -> {
                    int n = calls.incrementAndGet();
                    // 可变 list：menusForCurrentUser 内部对结果做 sort
                    return new ArrayList<>(List.of(menu(1L, 0L, "根" + n, 1), menu(10L, 1L, "第" + n + "次", 2)));
                });
    }

    /** 固定读替身：两个重载 + 用户菜单链路均返回稳定数据（写路径 / 驱逐测试用）。 */
    private void stubAllReadsFixed(MockedStatic<JooqWriters> writers) {
        stubCommonReads(writers);
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysMenuDO.class), any(),
                        any(OrderField[].class)))
                .thenReturn(new ArrayList<>(List.of(menu(1L, 0L, "根", 1), menu(10L, 1L, "工作台", 2))));
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysMenuDO.class), any()))
                .thenReturn(new ArrayList<>(List.of(menu(1L, 0L, "根", 1), menu(10L, 1L, "工作台", 2))));
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysUserRoleDO.class), any()))
                .thenReturn(List.of(userRole(1L, 100L)));
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysRoleMenuDO.class), any()))
                .thenReturn(List.of(roleMenu(100L, 10L)));
    }

    /** 仅用户菜单链路固定读替身（跨服务驱逐测试用，不涉及 tree()）。 */
    private void stubUserMenusFixed(MockedStatic<JooqWriters> writers) {
        stubCommonReads(writers);
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysUserRoleDO.class), any()))
                .thenReturn(List.of(userRole(1L, 100L)));
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysRoleMenuDO.class), any()))
                .thenReturn(List.of(roleMenu(100L, 10L)));
        writers.when(() -> JooqWriters.fetchList(any(), any(), eq(SysMenuDO.class), any()))
                .thenReturn(new ArrayList<>(List.of(menu(1L, 0L, "根", 1), menu(10L, 1L, "工作台", 2))));
    }

    private void stubMenuWrites(MockedStatic<JooqWriters> writers) {
        writers.when(() -> JooqWriters.count(any(), any(), any())).thenReturn(0L);
        writers.when(() -> JooqWriters.insert(any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.fetchById(any(), any(), eq(SysMenuDO.class), any()))
                .thenReturn(menu(10L, 1L, "工作台", 2));
        writers.when(() -> JooqWriters.updateById(any(), any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.deleteByColumn(any(), any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.delete(any(), any(), any(), eq(true))).thenAnswer(inv -> null);
    }

    private void stubRoleWrites(MockedStatic<JooqWriters> writers) {
        writers.when(() -> JooqWriters.fetchById(any(), any(), eq(SysRoleDO.class), any())).thenReturn(role(100L));
        writers.when(() -> JooqWriters.updateById(any(), any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.deleteByColumn(any(), any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.insert(any(), any(), any())).thenAnswer(inv -> null);
    }

    private void stubUserWrites(MockedStatic<JooqWriters> writers) {
        writers.when(() -> JooqWriters.fetchById(any(), any(), eq(SysUserDO.class), any())).thenReturn(user(1L));
        writers.when(() -> JooqWriters.fetchById(any(), any(), eq(SysRoleDO.class), any())).thenReturn(role(100L));
        writers.when(() -> JooqWriters.updateById(any(), any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.deleteByColumn(any(), any(), any(), any())).thenAnswer(inv -> null);
        writers.when(() -> JooqWriters.insert(any(), any(), any())).thenAnswer(inv -> null);
    }

    private Cache treeCache() {
        Cache cache = cacheManager.getCache(MenuService.MENU_TREE_CACHE);
        assertNotNull(cache);
        return cache;
    }

    private Cache userMenusCache() {
        Cache cache = cacheManager.getCache(MenuService.USER_MENUS_CACHE);
        assertNotNull(cache);
        return cache;
    }

    @SuppressWarnings("unchecked")
    private List<MenuTreeVO> cachedTree(Cache cache, Object key) {
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            return null;
        }
        return (List<MenuTreeVO>) wrapper.get();
    }

    private static String firstChildName(List<MenuTreeVO> tree) {
        return tree.get(0).children().get(0).menuName();
    }

    @Test
    void treeSecondReadServedFromCache() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubTreeChanging(writers);

            assertEquals("第1次", menuService.tree().get(0).menuName());
            // 命中缓存：打库替身按次返回不同值，二次读取仍是首次结果
            assertEquals("第1次", menuService.tree().get(0).menuName());

            List<MenuTreeVO> cached = cachedTree(treeCache(), MenuService.ALL_KEY);
            assertNotNull(cached);
            assertEquals("第1次", cached.get(0).menuName());
        }
    }

    @Test
    void userMenusCachedPerUserId() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubUserMenusChanging(writers);

            assertEquals("第1次", firstChildName(menuService.menusForCurrentUser(1L)));
            // 同用户二次读取命中缓存（否则会拿到「第2次」）
            assertEquals("第1次", firstChildName(menuService.menusForCurrentUser(1L)));
            // 不同用户独立键：回源拿到第二次结果
            assertEquals("第2次", firstChildName(menuService.menusForCurrentUser(2L)));

            assertEquals("第1次", firstChildName(cachedTree(userMenusCache(), 1L)));
            assertEquals("第2次", firstChildName(cachedTree(userMenusCache(), 2L)));
        }
    }

    @Test
    void menuCreateEvictsBothCaches() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubAllReadsFixed(writers);
            stubMenuWrites(writers);

            menuService.tree();
            menuService.menusForCurrentUser(1L);
            assertNotNull(cachedTree(treeCache(), MenuService.ALL_KEY));
            assertNotNull(cachedTree(userMenusCache(), 1L));

            menuService.create(new CreateMenuRequest(0L, "menu-new", "新菜单", "MENU",
                    "/new", null, "system:new:read", null, 1, 9, "ACTIVE"));

            assertNull(cachedTree(treeCache(), MenuService.ALL_KEY));
            assertNull(cachedTree(userMenusCache(), 1L));
        }
    }

    @Test
    void menuUpdateEvictsBothCaches() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubAllReadsFixed(writers);
            stubMenuWrites(writers);

            menuService.tree();
            menuService.menusForCurrentUser(1L);
            assertNotNull(cachedTree(treeCache(), MenuService.ALL_KEY));
            assertNotNull(cachedTree(userMenusCache(), 1L));

            // parentId 与现有值一致（桩返回 parentId=1），跳过层级移动校验分支
            menuService.update(10L, new UpdateMenuRequest(1L, "menu-10", "改名", "MENU",
                    "/x", null, "p", null, 1, 2, "ACTIVE"));

            assertNull(cachedTree(treeCache(), MenuService.ALL_KEY));
            assertNull(cachedTree(userMenusCache(), 1L));
        }
    }

    @Test
    void menuDeleteEvictsBothCaches() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubAllReadsFixed(writers);
            stubMenuWrites(writers);

            menuService.tree();
            menuService.menusForCurrentUser(1L);
            assertNotNull(cachedTree(treeCache(), MenuService.ALL_KEY));
            assertNotNull(cachedTree(userMenusCache(), 1L));

            menuService.delete(10L);

            assertNull(cachedTree(treeCache(), MenuService.ALL_KEY));
            assertNull(cachedTree(userMenusCache(), 1L));
        }
    }

    @Test
    void menuReorderEvictsBothCaches() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubAllReadsFixed(writers);
            stubMenuWrites(writers);

            menuService.tree();
            menuService.menusForCurrentUser(1L);
            assertNotNull(cachedTree(treeCache(), MenuService.ALL_KEY));
            assertNotNull(cachedTree(userMenusCache(), 1L));

            menuService.reorder(List.of(new MenuSortItem(10L, 5)));

            assertNull(cachedTree(treeCache(), MenuService.ALL_KEY));
            assertNull(cachedTree(userMenusCache(), 1L));
        }
    }

    @Test
    void roleMenuAssignmentEvictsUserMenus() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubUserMenusFixed(writers);
            stubRoleWrites(writers);

            menuService.menusForCurrentUser(1L);
            assertNotNull(cachedTree(userMenusCache(), 1L));

            // 角色菜单授权整体替换（apiIds 传 null 跳过接口绑定）
            roleService.update(100L, new CreateRoleRequest("ROLE-100", "测试角色", "BUSINESS",
                    "ACTIVE", 1, Set.of(10L), null));

            assertNull(cachedTree(userMenusCache(), 1L));
        }
    }

    @Test
    void userRoleChangeEvictsOnlyThatUser() {
        try (MockedStatic<JooqWriters> writers = mockStatic(JooqWriters.class)) {
            stubUserMenusFixed(writers);
            stubUserWrites(writers);

            menuService.menusForCurrentUser(1L);
            menuService.menusForCurrentUser(2L);
            assertNotNull(cachedTree(userMenusCache(), 1L));
            assertNotNull(cachedTree(userMenusCache(), 2L));

            // 角色整体替换（roleIds 非 null → assignRoles + tokenVersion.bumpUser）
            userService.update(1L, new UpdateUserRequest(null, null, null, null,
                    List.of(100L), null, null, null, null));

            assertNull(cachedTree(userMenusCache(), 1L));    // 该用户被精确驱逐
            assertNotNull(cachedTree(userMenusCache(), 2L)); // 其它用户不受影响
        }
    }
}
