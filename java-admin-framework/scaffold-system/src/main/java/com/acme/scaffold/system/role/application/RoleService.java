package com.acme.scaffold.system.role.application;
import com.acme.scaffold.common.error.CommonErrorCode;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.validation.RecordStatus;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.FetchSupport;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.menu.application.MenuService;
import com.acme.scaffold.system.role.domain.model.SysRoleApiDO;
import com.acme.scaffold.system.role.domain.model.SysRoleDO;
import com.acme.scaffold.system.role.domain.model.SysRoleDataScopeDO;
import com.acme.scaffold.system.role.domain.model.SysRoleMenuDO;
import com.acme.scaffold.system.role.dto.CreateRoleRequest;
import com.acme.scaffold.system.role.dto.RoleView;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色应用服务：角色 CRUD 与菜单 / 接口权限分配。持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final SqlTables.TableRef USER_ROLE = SqlTables.SYS_USER_ROLE;

    /** jOOQ 生成层桥接：与手写 DSL 的 {@link SqlTables#SYS_ROLE} 等价，列集一致。 */
    private static final com.acme.scaffold.mybatis.dsl.SqlTables.TableRef ROLE =
            SqlTables.SYS_ROLE;

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
        role.setStatus(RecordStatus.normalize(request.status()));
        role.setSortNo(request.sortNo() == null ? 0 : request.sortNo());
        SqlWriters.insert(dsl, ROLE, role);
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
            role.setStatus(RecordStatus.normalize(request.status()));
        }
        if (request.sortNo() != null) {
            role.setSortNo(request.sortNo());
        }
        SqlWriters.updateById(dsl, ROLE, id, role);
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
        Set<Long> menuIds = SqlWriters.fetchList(dsl, SqlTables.SYS_ROLE_MENU, SysRoleMenuDO.class,
                        SqlTables.SYS_ROLE_MENU.field("role_id", Long.class).eq(id)).stream()
                .map(SysRoleMenuDO::getMenuId).collect(Collectors.toSet());
        Set<Long> apiIds = SqlWriters.fetchList(dsl, SqlTables.SYS_ROLE_API, SysRoleApiDO.class,
                        SqlTables.SYS_ROLE_API.field("role_id", Long.class).eq(id)).stream()
                .map(SysRoleApiDO::getApiId).collect(Collectors.toSet());
        return new RoleView(role.getId(), role.getRoleCode(), role.getRoleName(), role.getRoleType(),
                role.getStatus(), role.getSortNo(), menuIds, apiIds);
    }

    /**
     * 角色全量列表（下拉/授权场景使用）。
     *
     * <p>角色为低基数主数据，正常规模下仅数十至数百条；此处下推 {@code LIMIT} 上限作为防御：
     * 避免异常数据增长时一次性把全表拖入堆内（与 P4「role/org 全量加载无分页」整改一致）。
     * 正常规模不会触顶；若触顶说明角色数据异常，需治理而非无限加载。</p>
     */
    private static final int MAX_ROLE_ROWS = 1000;

    public List<RoleView> list() {
        return SqlWriters.fetchTop(dsl, ROLE, SysRoleDO.class,
                        SqlWriters.notDeleted(ROLE), MAX_ROLE_ROWS,
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
        SqlWriters.deleteByColumn(dsl, USER_ROLE, "role_id", id);
        SqlWriters.delete(dsl, ROLE, id, true);
        SqlWriters.deleteByColumn(dsl, SqlTables.SYS_ROLE_MENU, "role_id", id);
        SqlWriters.deleteByColumn(dsl, SqlTables.SYS_ROLE_API, "role_id", id);
        // 数据权限：先清 CUSTOM 机构关联再清规则，否则残留孤儿行。
        // 该表无外键约束，漏删不会报错但会在角色重建（同 id）时读到旧机构。
        List<Long> ruleIds = SqlWriters.fetchList(dsl, SqlTables.SYS_ROLE_DATA_SCOPE, SysRoleDataScopeDO.class,
                        SqlTables.SYS_ROLE_DATA_SCOPE.field("role_id", Long.class).eq(id))
                .stream().map(SysRoleDataScopeDO::getId).toList();
        if (!ruleIds.isEmpty()) {
            dsl.delete(SqlTables.SYS_ROLE_DATA_SCOPE_ORG.table())
                    .where(SqlTables.SYS_ROLE_DATA_SCOPE_ORG.field("rule_id", Long.class).in(ruleIds))
                    .execute();
        }
        SqlWriters.deleteByColumn(dsl, SqlTables.SYS_ROLE_DATA_SCOPE, "role_id", id);
    }

    private void assignMenus(Long roleId, Set<Long> menuIds) {
        SqlWriters.deleteByColumn(dsl, SqlTables.SYS_ROLE_MENU, "role_id", roleId);
        if (menuIds == null) {
            return;
        }
        for (Long menuId : menuIds) {
            SysRoleMenuDO rm = new SysRoleMenuDO();
            rm.setRoleId(roleId);
            rm.setMenuId(menuId);
            SqlWriters.insert(dsl, SqlTables.SYS_ROLE_MENU, rm);
        }
    }

    private void assignApis(Long roleId, Set<Long> apiIds) {
        SqlWriters.deleteByColumn(dsl, SqlTables.SYS_ROLE_API, "role_id", roleId);
        if (apiIds == null) {
            return;
        }
        for (Long apiId : apiIds) {
            SysRoleApiDO ra = new SysRoleApiDO();
            ra.setRoleId(roleId);
            ra.setApiId(apiId);
            SqlWriters.insert(dsl, SqlTables.SYS_ROLE_API, ra);
        }
    }

    /** 角色编码是否已存在（全局唯一，含逻辑删除过滤）。开通流程用于幂等查重。 */
    public boolean existsByCode(String code) {
        return SqlWriters.count(dsl, ROLE,
                DSL.and(SqlWriters.notDeleted(ROLE),
                        ROLE.field("role_code", String.class).eq(code))) > 0;
    }

    private SysRoleDO get(Long id) {
        SysRoleDO role = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, ROLE, SysRoleDO.class, id), "角色不存在");
        return role;
    }
}
