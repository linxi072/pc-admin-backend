package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateMenuRequest;
import com.acme.scaffold.system.dto.MenuTreeVO;
import com.acme.scaffold.system.dto.UpdateMenuRequest;
import com.acme.scaffold.system.entity.SysMenuDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 菜单服务：菜单树与维护。持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final DSLContext dsl;

    /** 返回菜单树（按 parentId 聚合，按 sortNo 排序）。 */
    public List<MenuTreeVO> tree() {
        List<SysMenuDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                JooqWriters.notDeleted(JooqTables.SYS_MENU),
                JooqTables.SYS_MENU.field("sort_no", Integer.class).asc());
        Map<Long, List<SysMenuDO>> byParent = all.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));
        return buildChildren(0L, byParent);
    }

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
        JooqWriters.insert(dsl, JooqTables.SYS_MENU, menu);
        return menu.getId();
    }

    @Transactional
    public void delete(Long id) {
        long childCount = JooqWriters.count(dsl, JooqTables.SYS_MENU,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_MENU),
                        JooqTables.SYS_MENU.field("parent_id", Long.class).eq(id)));
        if (childCount > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "请先删除子菜单");
        }
        JooqWriters.delete(dsl, JooqTables.SYS_MENU, id, true);
    }

    @Transactional
    public void update(Long id, UpdateMenuRequest request) {
        SysMenuDO existing = JooqWriters.fetchById(dsl, JooqTables.SYS_MENU, SysMenuDO.class, id);
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
                SysMenuDO parent = JooqWriters.fetchById(dsl, JooqTables.SYS_MENU, SysMenuDO.class, newParentId);
                if (parent == null) {
                    throw new BusinessException(CommonErrorCode.NOT_FOUND, "父菜单不存在");
                }
                List<SysMenuDO> all = JooqWriters.fetchList(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                        JooqWriters.notDeleted(JooqTables.SYS_MENU));
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
        JooqWriters.updateById(dsl, JooqTables.SYS_MENU, id, menu);
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

    private List<MenuTreeVO> buildChildren(Long parentId, Map<Long, List<SysMenuDO>> byParent) {
        List<SysMenuDO> children = byParent.getOrDefault(parentId, List.of());
        List<MenuTreeVO> result = new ArrayList<>();
        for (SysMenuDO m : children) {
            result.add(new MenuTreeVO(m.getId(), m.getParentId(), m.getMenuCode(), m.getMenuName(),
                    m.getMenuType(), m.getRoutePath(), m.getComponentPath(), m.getPermissionCode(), m.getIcon(),
                    m.getVisible(), m.getSortNo(), m.getStatus(),
                    buildChildren(m.getId(), byParent)));
        }
        return result;
    }
}
