package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.dto.CreateRoleRequest;
import com.acme.scaffold.system.dto.RoleView;
import com.acme.scaffold.system.entity.SysRoleApiDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysRoleMenuDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色应用服务：角色 CRUD 与菜单 / 接口权限分配。持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final DSLContext dsl;

    @Transactional
    public Long create(CreateRoleRequest request) {
        if (existsByCode(request.roleCode())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "角色编码已存在");
        }
        SysRoleDO role = new SysRoleDO();
        role.setRoleCode(request.roleCode());
        role.setRoleName(request.roleName());
        role.setRoleType(request.roleType() == null ? "BUSINESS" : request.roleType());
        role.setStatus(request.status() == null ? "ACTIVE" : request.status());
        role.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        JooqWriters.insert(dsl, JooqTables.SYS_ROLE, role);
        assignMenus(role.getId(), request.menuIds());
        assignApis(role.getId(), request.apiIds());
        return role.getId();
    }

    @Transactional
    public void update(Long id, CreateRoleRequest request) {
        SysRoleDO role = get(id);
        role.setRoleName(request.roleName());
        if (request.roleType() != null) {
            role.setRoleType(request.roleType());
        }
        if (request.status() != null) {
            role.setStatus(request.status());
        }
        if (request.sortNo() != null) {
            role.setSortNo(request.sortNo());
        }
        JooqWriters.updateById(dsl, JooqTables.SYS_ROLE, id, role);
        assignMenus(id, request.menuIds());
        assignApis(id, request.apiIds());
    }

    public RoleView getView(Long id) {
        SysRoleDO role = get(id);
        Set<Long> menuIds = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_MENU, SysRoleMenuDO.class,
                        JooqTables.SYS_ROLE_MENU.field("role_id", Long.class).eq(id)).stream()
                .map(SysRoleMenuDO::getMenuId).collect(Collectors.toSet());
        Set<Long> apiIds = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_API, SysRoleApiDO.class,
                        JooqTables.SYS_ROLE_API.field("role_id", Long.class).eq(id)).stream()
                .map(SysRoleApiDO::getApiId).collect(Collectors.toSet());
        return new RoleView(role.getId(), role.getRoleCode(), role.getRoleName(), role.getRoleType(),
                role.getStatus(), role.getSortNo(), menuIds, apiIds);
    }

    public List<RoleView> list() {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                        JooqWriters.notDeleted(JooqTables.SYS_ROLE),
                        JooqTables.SYS_ROLE.field("sort_no", Integer.class).asc())
                .stream().map(RoleView::from).collect(Collectors.toList());
    }

    @Transactional
    public void delete(Long id) {
        JooqWriters.delete(dsl, JooqTables.SYS_ROLE, id, true);
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_ROLE_MENU, "role_id", id);
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_ROLE_API, "role_id", id);
    }

    private void assignMenus(Long roleId, Set<Long> menuIds) {
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_ROLE_MENU, "role_id", roleId);
        if (menuIds == null) {
            return;
        }
        for (Long menuId : menuIds) {
            SysRoleMenuDO rm = new SysRoleMenuDO();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            JooqWriters.insert(dsl, JooqTables.SYS_ROLE_MENU, rm);
        }
    }

    private void assignApis(Long roleId, Set<Long> apiIds) {
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_ROLE_API, "role_id", roleId);
        if (apiIds == null) {
            return;
        }
        for (Long apiId : apiIds) {
            SysRoleApiDO ra = new SysRoleApiDO();
            ra.setRoleId(roleId);
            ra.setApiId(apiId);
            JooqWriters.insert(dsl, JooqTables.SYS_ROLE_API, ra);
        }
    }

    private boolean existsByCode(String code) {
        return JooqWriters.count(dsl, JooqTables.SYS_ROLE,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ROLE),
                        JooqTables.SYS_ROLE.field("role_code", String.class).eq(code))) > 0;
    }

    private SysRoleDO get(Long id) {
        SysRoleDO role = JooqWriters.fetchById(dsl, JooqTables.SYS_ROLE, SysRoleDO.class, id);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }
}
