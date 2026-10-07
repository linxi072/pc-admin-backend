package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.security.permission.DataScopeProvider;
import com.acme.scaffold.security.permission.DataScopeResult;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.dto.UpdateUserRequest;
import com.acme.scaffold.system.dto.UserQuery;
import com.acme.scaffold.system.dto.UserView;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户应用服务：CRUD、单角色/单部门绑定、密码重置，并在列表查询中演示数据权限扩展点。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ（动态 DSL）。
 * <p><b>约束（V6 收敛）</b>：用户仅绑定单个角色（sys_user.role_id）与单个部门（sys_user.org_id），
 * 不再维护 sys_user_role / sys_user_org 多对多关联，权限判定与数据范围均按单值直读。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;
    private final DataScopeProvider dataScopeProvider;
    private final SecurityContextFacade securityContextFacade;

    @Transactional
    public Long create(CreateUserRequest request) {
        if (existsByUsername(request.username())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "用户名已存在");
        }
        SysUserDO user = new SysUserDO();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setMobile(request.mobile());
        user.setEmail(request.email());
        user.setOrgId(request.orgId());
        // 兼容期同步维护 primary_org_id，保证尚未切换的旧读取路径一致
        user.setPrimaryOrgId(request.orgId());
        user.setRoleId(requireRole(request.roleId()));
        user.setStatus("ACTIVE");
        user.setPasswordChangedAt(LocalDateTime.now());
        JooqWriters.insert(dsl, JooqTables.SYS_USER, user);
        return user.getId();
    }

    @Transactional
    public void update(Long id, UpdateUserRequest request) {
        SysUserDO user = get(id);
        if (request.displayName() != null) {
            user.setDisplayName(request.displayName());
        }
        if (request.mobile() != null) {
            user.setMobile(request.mobile());
        }
        if (request.email() != null) {
            user.setEmail(request.email());
        }
        if (request.orgId() != null) {
            requireOrg(request.orgId());
            user.setOrgId(request.orgId());
            user.setPrimaryOrgId(request.orgId());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        if (request.roleId() != null) {
            user.setRoleId(requireRole(request.roleId()));
        }
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, id, user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        SysUserDO user = get(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setPasswordExpired(0);
        user.setTokenVersion((user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1);
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, id, user);
    }

    /**
     * 物理删除用户（硬删除）。
     * <p>V6 收敛后用户与角色/部门的关联已落到 sys_user 本列，删除用户无需再清理关联表。
     */
    @Transactional
    public void delete(Long id) {
        get(id); // 校验存在性，不存在抛 NOT_FOUND
        JooqWriters.delete(dsl, JooqTables.SYS_USER, id, false);
    }

    public UserView getView(Long id) {
        SysUserDO user = get(id);
        return toView(user);
    }

    public PageResult<UserView> list(UserQuery query) {
        CurrentPrincipal principal = securityContextFacade.getCurrentPrincipal().orElse(null);
        DataScopeResult scope = principal == null ? DataScopeResult.all()
                : dataScopeProvider.resolve(principal.userId(), UserQuery.RESOURCE_CODE);

        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(JooqTables.SYS_USER));
        if (StringUtils.hasText(query.username())) {
            conds.add(DSL.or(
                    JooqTables.SYS_USER.field("username", String.class).like("%" + query.username() + "%"),
                    JooqTables.SYS_USER.field("display_name", String.class).like("%" + query.username() + "%")));
        }
        if (StringUtils.hasText(query.status())) {
            conds.add(JooqTables.SYS_USER.field("status", String.class).eq(query.status()));
        }
        if (query.orgId() != null) {
            conds.add(JooqTables.SYS_USER.field("org_id", Long.class).eq(query.orgId()));
        }
        if (query.roleId() != null) {
            conds.add(JooqTables.SYS_USER.field("role_id", Long.class).eq(query.roleId()));
        }
        // 数据权限：SELF 按本人过滤，其余受限范围按机构集合过滤
        if (scope.selfOnly() && principal != null) {
            conds.add(JooqTables.SYS_USER.field("id", Long.class).eq(principal.userId()));
        } else if (!scope.unrestricted()) {
            conds.add(JooqTables.SYS_USER.field("org_id", Long.class).in(scope.orgIds()));
        }

        PageResult<SysUserDO> page = JooqWriters.page(dsl, JooqTables.SYS_USER, SysUserDO.class,
                DSL.and(conds), query.toPageQuery(),
                JooqTables.SYS_USER.field("id", Long.class).desc());

        Map<Long, SysOrgDO> orgCache = loadOrgs(page.records());
        Map<Long, SysRoleDO> roleCache = loadRoles(page.records());
        List<UserView> records = page.records().stream()
                .map(u -> toView(u, orgCache, roleCache))
                .collect(Collectors.toList());
        return new PageResult<>(page.page(), page.size(), page.total(), records);
    }

    /** 校验角色存在且未删除，返回角色ID。 */
    private Long requireRole(Long roleId) {
        if (roleId == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "角色不能为空，用户仅可绑定单个角色");
        }
        SysRoleDO role = JooqWriters.fetchById(dsl, JooqTables.SYS_ROLE, SysRoleDO.class, roleId);
        if (role == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "角色不存在");
        }
        if (!"ACTIVE".equals(role.getStatus())) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "角色已停用，无法绑定");
        }
        return roleId;
    }

    /** 校验部门存在且未删除（部门为可选绑定，故允许 null）。 */
    private void requireOrg(Long orgId) {
        SysOrgDO org = JooqWriters.fetchById(dsl, JooqTables.SYS_ORG, SysOrgDO.class, orgId);
        if (org == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "部门不存在");
        }
    }

    private UserView toView(SysUserDO user) {
        return toView(user, loadOrgs(List.of(user)), loadRoles(List.of(user)));
    }

    private UserView toView(SysUserDO user, Map<Long, SysOrgDO> orgCache, Map<Long, SysRoleDO> roleCache) {
        SysOrgDO org = user.getOrgId() == null ? null : orgCache.get(user.getOrgId());
        SysRoleDO role = user.getRoleId() == null ? null : roleCache.get(user.getRoleId());
        return UserView.from(user,
                org == null ? null : org.getOrgName(),
                role == null ? null : role.getRoleName(),
                role == null ? null : role.getRoleCode());
    }

    /** 批量装载部门，避免列表查询产生 N+1。 */
    private Map<Long, SysOrgDO> loadOrgs(List<SysUserDO> users) {
        Set<Long> ids = users.stream().map(SysUserDO::getOrgId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysOrgDO> map = new HashMap<>();
        JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG),
                                JooqTables.SYS_ORG.field("id", Long.class).in(ids)))
                .forEach(o -> map.put(o.getId(), o));
        return map;
    }

    /** 批量装载角色，避免列表查询产生 N+1。 */
    private Map<Long, SysRoleDO> loadRoles(List<SysUserDO> users) {
        Set<Long> ids = users.stream().map(SysUserDO::getRoleId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, SysRoleDO> map = new HashMap<>();
        JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ROLE),
                                JooqTables.SYS_ROLE.field("id", Long.class).in(ids)))
                .forEach(r -> map.put(r.getId(), r));
        return map;
    }

    private boolean existsByUsername(String username) {
        return JooqWriters.count(dsl, JooqTables.SYS_USER,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("username", String.class).eq(username))) > 0;
    }

    private SysUserDO get(Long id) {
        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, id);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
