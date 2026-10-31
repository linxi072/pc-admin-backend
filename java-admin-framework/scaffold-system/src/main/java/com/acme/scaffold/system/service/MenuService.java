package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.tree.TreeBuilder;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.system.dto.CreateMenuRequest;
import com.acme.scaffold.system.dto.MenuSortItem;
import com.acme.scaffold.system.dto.MenuTreeVO;
import com.acme.scaffold.system.dto.UpdateMenuRequest;
import com.acme.scaffold.system.entity.SysMenuDO;
import com.acme.scaffold.system.entity.SysRoleMenuDO;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务：菜单树与维护。持久层由 MyBatis-Plus 迁移为 jOOQ。
 *
 * <p>缓存（M5 续）：{@link #tree()} 为管理端菜单树热点读（单一键），
 * {@link #menusForCurrentUser(Long)} 为每个登录用户的动态路由源（按用户键），
 * 均纳入 Spring Cache（TTL 由 {@code app.cache.ttl} 控制）。写路径
 * （增 / 删 / 改 / 排序）整缓存驱逐；角色菜单绑定（{@code RoleService}）与
 * 用户角色变更（{@code UserService}）按各自语义驱逐 {@value #USER_MENUS_CACHE}。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private static final JooqTables.TableRef USER_ROLE = JooqTables.SYS_USER_ROLE;

    /** 管理端菜单树缓存名（单一 {@value #ALL_KEY} 键，监控页 / 清理端点按此定位）。 */
    public static final String MENU_TREE_CACHE = "menu-tree";

    /** 用户菜单树缓存名（键为用户 id，跨服务驱逐时引用此常量）。 */
    public static final String USER_MENUS_CACHE = "user-menus";

    /** {@value #MENU_TREE_CACHE} 的固定键（全站唯一一棵管理菜单树）。 */
    public static final String ALL_KEY = "ALL";

    private final DSLContext dsl;

    /** 桥接自代码生成层 {@code Tables.SYS_MENU} 的类型安全表引用（B15）。 */
    private static final JooqTables.TableRef MENU = JooqTables.SYS_MENU;

    /**
     * 返回菜单树（按 parentId 聚合排序）。
     *
     * <p>排序规则：同级内 {@code sort_no ASC, id ASC}。二级键 id 保证 sortNo 相同或未设置（默认 0）时
     * 顺序确定（按创建先后），避免同值情况下依赖数据库返回顺序导致列表展示不稳定。
     *
     * <p>缓存：管理端菜单管理页每次进入都会加载整树，属热点读；菜单写操作统一整缓存驱逐。
     */
    @Cacheable(cacheNames = MENU_TREE_CACHE, key = "'" + ALL_KEY + "'")
    public List<MenuTreeVO> tree() {
        List<SysMenuDO> all = JooqWriters.fetchList(dsl, MENU, SysMenuDO.class,
                JooqWriters.notDeleted(MENU),
                JooqTables.SYS_MENU.field("sort_no", Integer.class).asc(),
                JooqTables.SYS_MENU.field("id", Long.class).asc());
        // 层级组装复用 common/tree/TreeBuilder（与 OrgService 同一实现，含环防护）
        return TreeBuilder.build(all, SysMenuDO::getId, MenuService::parentIdOf,
                m -> parentIdOf(m) == 0L, MenuService::toMenuTreeVO);
    }

    /** parentId 归一：null 视作顶级（0）。TreeBuilder 分组 / 顶层判定与 VO 映射共用。 */
    private static Long parentIdOf(SysMenuDO m) {
        return m.getParentId() == null ? 0L : m.getParentId();
    }

    private static MenuTreeVO toMenuTreeVO(SysMenuDO m, List<MenuTreeVO> children) {
        return new MenuTreeVO(m.getId(), m.getParentId(), m.getMenuCode(), m.getMenuName(),
                m.getMenuType(), m.getRoutePath(), m.getComponentPath(), m.getPermissionCode(), m.getIcon(),
                m.getVisible(), m.getSortNo(), m.getStatus(), children);
    }

    /**
     * 返回当前登录用户「角色范围内」的菜单树（动态加载的核心）。
     *
     * <p>链路：用户 → 角色（sys_user_role）→ 菜单（sys_role_menu）→ 菜单树。
     * 仅纳入可见且启用的授权菜单，并补全其祖先链，保证前端渲染的树结构连通（子菜单不会悬空）。
     * 菜单数据完全源自角色配置，无任何前端硬编码，支持后续角色 / 权限的灵活调整。
     *
     * <p>缓存：登录 / 刷新 / 前端路由动态加载均走此方法，按用户 id 缓存；
     * 菜单写操作、角色菜单绑定变更、用户角色变更均会驱逐（见类注释）。
     * 菜单树仅驱动侧边栏展示，权限判定始终以 {@code sys_role_menu} 实时查询为准，
     * 缓存陈旧不影响授权安全（最坏多展示一个 TTL 周期的旧菜单）。
     */
    @Cacheable(cacheNames = USER_MENUS_CACHE, key = "#userId")
    public List<MenuTreeVO> menusForCurrentUser(Long userId) {
        List<Long> roleIds = JooqWriters.fetchList(dsl, USER_ROLE, SysUserRoleDO.class,
                        USER_ROLE.field("user_id", Long.class).eq(userId)).stream()
                .map(SysUserRoleDO::getRoleId).distinct().toList();
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> authorizedMenuIds = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_MENU, SysRoleMenuDO.class,
                        JooqTables.SYS_ROLE_MENU.field("role_id", Long.class).in(roleIds)).stream()
                .map(SysRoleMenuDO::getMenuId).collect(Collectors.toSet());
        if (authorizedMenuIds.isEmpty()) {
            return List.of();
        }
        List<SysMenuDO> all = JooqWriters.fetchList(dsl, MENU, SysMenuDO.class,
                JooqWriters.notDeleted(MENU));
        // 与 tree() 保持同一排序规则：sortNo ASC，二级键 id ASC 保证同值顺序确定
        all.sort(Comparator.comparingInt((SysMenuDO m) -> m.getSortNo() == null ? 0 : m.getSortNo())
                .thenComparing(SysMenuDO::getId));
        Map<Long, SysMenuDO> byId = all.stream()
                .collect(Collectors.toMap(SysMenuDO::getId, m -> m, (a, b) -> a));
        Set<Long> visibleIds = authorizedMenuIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(m -> m.getVisible() != null && m.getVisible() == 1)
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .map(SysMenuDO::getId)
                .collect(Collectors.toSet());
        // 补全祖先链，避免子菜单悬空导致渲染异常
        Set<Long> expanded = new HashSet<>(visibleIds);
        for (Long id : new ArrayList<>(visibleIds)) {
            SysMenuDO node = byId.get(id);
            Long pid = node != null ? node.getParentId() : 0L;
            while (pid != null && pid != 0L && byId.containsKey(pid) && expanded.add(pid)) {
                pid = byId.get(pid).getParentId();
            }
        }
        // expanded 为 HashSet，直接流式迭代会按其哈希序输出，令上方 all.sort 失效、
        // 侧边栏顺序与 sort_no 脱节；改从已排序的 all 过滤，输出与 tree() 同序
        // （sort_no ASC，二级键 id ASC），仅修正展示顺序，菜单集合不变。
        List<SysMenuDO> included = all.stream().filter(m -> expanded.contains(m.getId())).toList();
        // 层级组装复用 common/tree/TreeBuilder（与 tree() / OrgService 同一实现）
        return TreeBuilder.build(included, SysMenuDO::getId, MenuService::parentIdOf,
                m -> parentIdOf(m) == 0L, MenuService::toMenuTreeVO);
    }

    /** 创建菜单。写入后两个菜单缓存均整缓存驱逐（新菜单可能进入任意用户的可见集合）。 */
    @CacheEvict(cacheNames = {MENU_TREE_CACHE, USER_MENUS_CACHE}, allEntries = true)
    @Transactional
    public Long create(CreateMenuRequest request) {
        SysMenuDO menu = new SysMenuDO();
        menu.setParentId(request.parentId() == null ? 0L : request.parentId());
        menu.setMenuCode(request.menuCode());
        menu.setMenuName(request.menuName());
        menu.setMenuType(request.menuType());
        menu.setRoutePath(request.routePath());
        menu.setComponentPath(request.componentPath());
        menu.setPermissionCode(request.permissionCode());
        menu.setIcon(request.icon());
        menu.setVisible(request.visible() == null ? 1 : request.visible());
        menu.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        menu.setStatus(request.status() == null ? "ACTIVE" : request.status());
        JooqWriters.insert(dsl, MENU, menu);
        return menu.getId();
    }

    /** 删除菜单（级联清理角色-菜单关联后整缓存驱逐）。 */
    @CacheEvict(cacheNames = {MENU_TREE_CACHE, USER_MENUS_CACHE}, allEntries = true)
    @Transactional
    public void delete(Long id) {
        long childCount = JooqWriters.count(dsl, MENU,
                DSL.and(JooqWriters.notDeleted(MENU),
                        JooqTables.SYS_MENU.field("parent_id", Long.class).eq(id)));
        if (childCount > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先删除子菜单");
        }
        // 角色-菜单关联先清：该表无外键约束，漏删会残留孤儿行（菜单已删但角色仍"挂"着它，
        // 数据卫生失控且重建同 id 菜单时历史授权不会恢复）。与 RoleService.delete 的
        // 级联清理互为补充——那里按 role_id 清，这里按 menu_id 清。
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_ROLE_MENU, "menu_id", id);
        JooqWriters.delete(dsl, MENU, id, true);
    }

    /**
     * 更新菜单。可见性 / 状态 / 层级变更都会改变用户菜单树，故整缓存驱逐。
     */
    @CacheEvict(cacheNames = {MENU_TREE_CACHE, USER_MENUS_CACHE}, allEntries = true)
    @Transactional
    public void update(Long id, UpdateMenuRequest request) {
        SysMenuDO existing = JooqWriters.fetchById(dsl, MENU, SysMenuDO.class, id);
        if (existing == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "菜单不存在");
        }
        Long newParentId = request.parentId() == null ? 0L : request.parentId();
        // 层级移动校验：parent_id 不变时跳过；变更时禁止挂到自身或其子孙之下（防环）
        if (!newParentId.equals(existing.getParentId())) {
            if (newParentId.equals(id)) {
                throw new BusinessException(CommonErrorCode.CONFLICT, "不能将菜单挂到自身之下");
            }
            if (newParentId != 0L) {
                SysMenuDO parent = JooqWriters.fetchById(dsl, MENU, SysMenuDO.class, newParentId);
                if (parent == null) {
                    throw new BusinessException(CommonErrorCode.NOT_FOUND, "父菜单不存在");
                }
                List<SysMenuDO> all = JooqWriters.fetchList(dsl, MENU, SysMenuDO.class,
                        JooqWriters.notDeleted(MENU));
                if (collectDescendantIds(all, id).contains(newParentId)) {
                    throw new BusinessException(CommonErrorCode.CONFLICT, "不能将菜单移动到其子菜单之下");
                }
            }
        }
        // version、tenant_id 维持原值（避免乐观锁重置 / 跨租户串改），其余字段按请求覆盖
        SysMenuDO menu = new SysMenuDO();
        menu.setParentId(newParentId);
        menu.setTenantId(existing.getTenantId());
        menu.setVersion(existing.getVersion());
        menu.setMenuCode(request.menuCode());
        menu.setMenuName(request.menuName());
        menu.setMenuType(request.menuType());
        menu.setRoutePath(request.routePath());
        menu.setComponentPath(request.componentPath());
        menu.setPermissionCode(request.permissionCode());
        menu.setIcon(request.icon());
        menu.setVisible(request.visible() == null ? existing.getVisible() : request.visible());
        menu.setSortNo(request.sortNo() == null ? existing.getSortNo() : request.sortNo());
        menu.setStatus(request.status() == null ? existing.getStatus() : request.status());
        JooqWriters.updateById(dsl, MENU, id, menu);
    }

    /**
     * 批量调整菜单排序（拖拽排序 / 上移下移的持久化入口）。
     *
     * <p>仅更新 {@code sort_no} 一列：先按 id 取回现有行，复制其全部字段后仅覆盖 sortNo，
     * 避免 SysMenuDO 字段默认值（parentId=0、status=ACTIVE 等非 null 默认值）经 updateById
     * 的 NOT_NULL 策略误覆盖其他列（与 {@link #update} 同一范式）。任一菜单不存在即整体回滚。
     *
     * @param items 排序项列表（通常为同一父级下的全部子菜单，前端拖拽后按新顺序重算 sortNo）
     */
    /** 批量调整排序：排序直接影响两棵树的展示顺序，整缓存驱逐。 */
    @CacheEvict(cacheNames = {MENU_TREE_CACHE, USER_MENUS_CACHE}, allEntries = true)
    @Transactional
    public void reorder(List<MenuSortItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (MenuSortItem item : items) {
            SysMenuDO existing = JooqWriters.fetchById(dsl, MENU, SysMenuDO.class, item.id());
            if (existing == null) {
                throw new BusinessException(CommonErrorCode.NOT_FOUND, "菜单不存在: " + item.id());
            }
            // 复制现有行，仅覆盖 sortNo（version/tenant_id 维持原值，与 update() 一致）
            SysMenuDO menu = new SysMenuDO();
            menu.setParentId(existing.getParentId());
            menu.setTenantId(existing.getTenantId());
            menu.setVersion(existing.getVersion());
            menu.setMenuCode(existing.getMenuCode());
            menu.setMenuName(existing.getMenuName());
            menu.setMenuType(existing.getMenuType());
            menu.setRoutePath(existing.getRoutePath());
            menu.setComponentPath(existing.getComponentPath());
            menu.setPermissionCode(existing.getPermissionCode());
            menu.setIcon(existing.getIcon());
            menu.setVisible(existing.getVisible());
            menu.setKeepAlive(existing.getKeepAlive());
            menu.setExternalUrl(existing.getExternalUrl());
            menu.setStatus(existing.getStatus());
            menu.setSortNo(item.sortNo());
            JooqWriters.updateById(dsl, MENU, item.id(), menu);
        }
    }

    /** 收集指定节点的全部子孙 id（含自身用于环路判断）。 */
    private Set<Long> collectDescendantIds(List<SysMenuDO> all, Long rootId) {
        Map<Long, List<SysMenuDO>> byParent = all.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        Set<Long> result = new HashSet<>();
        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long cur = stack.pop();
            for (SysMenuDO child : byParent.getOrDefault(cur, List.of())) {
                if (result.add(child.getId())) {
                    stack.push(child.getId());
                }
            }
        }
        return result;
    }
}
