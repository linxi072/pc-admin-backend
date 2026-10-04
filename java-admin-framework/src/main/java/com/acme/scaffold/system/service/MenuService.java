package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateMenuRequest;
import com.acme.scaffold.system.dto.MenuTreeVO;
import com.acme.scaffold.system.entity.SysMenuDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private List<MenuTreeVO> buildChildren(Long parentId, Map<Long, List<SysMenuDO>> byParent) {
        List<SysMenuDO> children = byParent.getOrDefault(parentId, List.of());
        List<MenuTreeVO> result = new ArrayList<>();
        for (SysMenuDO m : children) {
            result.add(new MenuTreeVO(m.getId(), m.getParentId(), m.getMenuCode(), m.getMenuName(),
                    m.getMenuType(), m.getRoutePath(), m.getPermissionCode(), m.getSortNo(), m.getStatus(),
                    buildChildren(m.getId(), byParent)));
        }
        return result;
    }
}
