package com.acme.scaffold.system.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import com.acme.scaffold.system.dto.CreateRoleRequest;
import com.acme.scaffold.system.dto.RoleView;
import com.acme.scaffold.system.entity.SysRoleApiDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysRoleDataScopeDO;
import com.acme.scaffold.system.entity.SysRoleMenuDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.cache.annotation.CacheEvict;
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

    private static final JooqTables.TableRef USER_ROLE = JooqTables.toTableRef(Tables.SYS_USER_ROLE);

    /** jOOQ 生成层桥接：与手写 DSL 的 {@link JooqTables#SYS_ROLE} 等价，列集一致。 */
    private static final com.acme.scaffold.jooq.dsl.JooqTables.TableRef ROLE =
            JooqTables.toTableRef(Tables.SYS_ROLE);

    private final DSLContext dsl;

    /**
     * 创建角色。菜单授权影响用户菜单树（{@link MenuService#USER_MENUS_CACHE}），
     * 整缓存驱逐（新建角色尚无用户绑定，此处为防御性语义）。
     */
    @CacheEvict(cacheNames = MenuService.USER_MENUS_CACHE, allEntries = true)
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
        JooqWriters.insert(dsl, ROLE, role);
        assignMenus(role.getId(), request.menuIds());
        assignApis(role.getId(), request.apiIds());
        return role.getId();
    }

    /**
     * 更新角色。menuIds / apiIds 非 null 时整体替换绑定：菜单授权变化影响所有
     * 持有该角色的用户的菜单树，故整缓存驱逐 {@value MenuService#USER_MENUS_CACHE}。
     */
    @CacheEvict(cacheNames = MenuService.USER_MENUS_CACHE, allEntries = true)
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
        JooqWriters.updateById(dsl, ROLE, id, role);
        // 绑定语义：menuIds / apiIds 为 null 表示「本次不改动绑定」（保留原授权），
        // 空集合表示「清空全部绑定」。原实现无条件重建绑定，调用方局部更新角色信息
        // （如只改角色名）时漏传即把菜单/接口授权全部清空——契约二义且误清后果严重。
        // 前端编辑角色始终回填两个集合（空集合=全部取消勾选），语义切换对其无感。
        if (request.menuIds() != null) {
            assignMenus(id, request.menuIds());
        }
        if (request.apiIds() != null) {
            assignApis(id, request.apiIds());
        }
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
        return JooqWriters.fetchList(dsl, ROLE, SysRoleDO.class,
                        JooqWriters.notDeleted(ROLE),
                        ROLE.field("sort_no", Integer.class).asc())
                .stream().map(RoleView::from).collect(Collectors.toList());
    }

    /**
     * 删除角色（级联清理用户-角色、角色-菜单、角色-接口关联）。
     * 受影响用户的菜单树随缓存整缓存驱逐。
     */
    @CacheEvict(cacheNames = MenuService.USER_MENUS_CACHE, allEntries = true)
    @Transactional
    public void delete(Long id) {
        // 用户-角色关联先清：该表无外键约束，漏删会残留孤儿行（角色已不存在但用户仍"挂"着它，
        // 权限计算靠 notDeleted 兜底过滤虽不泄露，但数据卫生失控且角色重建后历史绑定不会恢复）
        JooqWriters.deleteByColumn(dsl, USER_ROLE, "role_id", id);
        JooqWriters.delete(dsl, ROLE, id, true);
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_ROLE_MENU, "role_id", id);
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_ROLE_API, "role_id", id);
        // 数据权限：先清 CUSTOM 机构关联再清规则，否则残留孤儿行。
        // 该表无外键约束，漏删不会报错但会在角色重建（同 id）时读到旧机构。
        List<Long> ruleIds = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE, SysRoleDataScopeDO.class,
                        JooqTables.SYS_ROLE_DATA_SCOPE.field("role_id", Long.class).eq(id))
                .stream().map(SysRoleDataScopeDO::getId).toList();
        if (!ruleIds.isEmpty()) {
            dsl.delete(JooqTables.SYS_ROLE_DATA_SCOPE_ORG.table())
                    .where(JooqTables.SYS_ROLE_DATA_SCOPE_ORG.field("rule_id", Long.class).in(ruleIds))
                    .execute();
        }
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_ROLE_DATA_SCOPE, "role_id", id);
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

    /** 角色编码是否已存在（全局唯一，含逻辑删除过滤）。开通流程用于幂等查重。 */
    public boolean existsByCode(String code) {
        return JooqWriters.count(dsl, ROLE,
                DSL.and(JooqWriters.notDeleted(ROLE),
                        ROLE.field("role_code", String.class).eq(code))) > 0;
    }

    private SysRoleDO get(Long id) {
        SysRoleDO role = JooqWriters.fetchById(dsl, ROLE, SysRoleDO.class, id);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }
}
