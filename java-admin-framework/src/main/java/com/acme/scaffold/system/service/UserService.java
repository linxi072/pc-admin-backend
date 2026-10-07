package com.acme.scaffold.system.service;

import com.acme.scaffold.common.api.PageResult;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.security.permission.DataScopeProvider;
import com.acme.scaffold.security.permission.PermissionService;
import com.acme.scaffold.system.dto.CreateUserRequest;
import com.acme.scaffold.system.dto.UpdateUserRequest;
import com.acme.scaffold.system.dto.UserQuery;
import com.acme.scaffold.system.dto.UserView;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.entity.SysUserRoleDO;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户应用服务：CRUD、角色分配、密码重置，并在列表查询中演示数据权限扩展点。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ（动态 DSL）。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final DSLContext dsl;
    private final PermissionService permissionService;
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
        user.setPrimaryOrgId(request.primaryOrgId());
        user.setStatus("ACTIVE");
        user.setPasswordChangedAt(LocalDateTime.now());
        JooqWriters.insert(dsl, JooqTables.SYS_USER, user);
        assignRoles(user.getId(), request.roleIds());
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
        if (request.primaryOrgId() != null) {
            user.setPrimaryOrgId(request.primaryOrgId());
        }
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, id, user);
        if (request.roleIds() != null) {
            assignRoles(id, request.roleIds());
        }
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
     * 物理删除用户及其角色关联（硬删除）。
     * 调用 JooqWriters.delete(..., false) 绕过逻辑删除，直接移除数据行；
     * 同时清理 SYS_USER_ROLE 关联，避免孤儿记录。
     */
    @Transactional
    public void delete(Long id) {
        get(id); // 校验存在性，不存在抛 NOT_FOUND
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_USER_ROLE, "user_id", id);
        JooqWriters.delete(dsl, JooqTables.SYS_USER, id, false);
    }

    public UserView getView(Long id) {
        SysUserDO user = get(id);
        return UserView.from(user, permissionService.getRoleCodes(user.getId()));
    }

    public PageResult<UserView> list(UserQuery query) {
        CurrentPrincipal principal = securityContextFacade.getCurrentPrincipal().orElse(null);
        Set<Long> orgIds = principal == null ? Set.of() :
                dataScopeProvider.resolveOrgIds(principal.userId(), "system:user");

        List<Condition> conds = new ArrayList<>();
        conds.add(JooqWriters.notDeleted(JooqTables.SYS_USER));
        if (StringUtils.hasText(query.username())) {
            conds.add(JooqTables.SYS_USER.field("username", String.class).like("%" + query.username() + "%"));
        }
        if (StringUtils.hasText(query.status())) {
            conds.add(JooqTables.SYS_USER.field("status", String.class).eq(query.status()));
        }
        if (query.orgId() != null) {
            conds.add(JooqTables.SYS_USER.field("primary_org_id", Long.class).eq(query.orgId()));
        }
        if (orgIds != null && !orgIds.isEmpty()) {
            conds.add(JooqTables.SYS_USER.field("primary_org_id", Long.class).in(orgIds));
        }

        PageResult<SysUserDO> page = JooqWriters.page(dsl, JooqTables.SYS_USER, SysUserDO.class,
                DSL.and(conds), query.toPageQuery(),
                JooqTables.SYS_USER.field("id", Long.class).desc());

        List<UserView> records = page.records().stream()
                .map(u -> UserView.from(u, permissionService.getRoleCodes(u.getId())))
                .collect(Collectors.toList());
        return new PageResult<>(page.page(), page.size(), page.total(), records);
    }

    private void assignRoles(Long userId, Set<Long> roleIds) {
        JooqWriters.deleteByColumn(dsl, JooqTables.SYS_USER_ROLE, "user_id", userId);
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            SysUserRoleDO ur = new SysUserRoleDO();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            ur.setScopeOrgId(0L);
            JooqWriters.insert(dsl, JooqTables.SYS_USER_ROLE, ur);
        }
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
