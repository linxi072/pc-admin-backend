package com.acme.scaffold.security.permission;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.entity.SysApiResourceDO;
import com.acme.scaffold.system.entity.SysMenuDO;
import com.acme.scaffold.system.entity.SysRoleApiDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysRoleMenuDO;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限聚合：根据用户角色汇总接口权限码与角色码，供 JWT 签发与接口鉴权使用。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final DSLContext dsl;

    public Set<String> getPermissions(Long userId) {
        Set<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        Set<String> permissions = new HashSet<>();

        List<Long> apiIds = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_API, SysRoleApiDO.class,
                        JooqTables.SYS_ROLE_API.field("role_id", Long.class).in(roleIds)).stream()
                .map(SysRoleApiDO::getApiId).collect(Collectors.toList());
        if (!apiIds.isEmpty()) {
            JooqWriters.fetchList(dsl, JooqTables.SYS_API_RESOURCE, SysApiResourceDO.class,
                            DSL.and(JooqWriters.notDeleted(JooqTables.SYS_API_RESOURCE),
                                    JooqTables.SYS_API_RESOURCE.field("id", Long.class).in(apiIds)))
                    .forEach(a -> permissions.add(a.getPermissionCode()));
        }

        List<Long> menuIds = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_MENU, SysRoleMenuDO.class,
                        JooqTables.SYS_ROLE_MENU.field("role_id", Long.class).in(roleIds)).stream()
                .map(SysRoleMenuDO::getMenuId).collect(Collectors.toList());
        if (!menuIds.isEmpty()) {
            JooqWriters.fetchList(dsl, JooqTables.SYS_MENU, SysMenuDO.class,
                            DSL.and(JooqWriters.notDeleted(JooqTables.SYS_MENU),
                                    JooqTables.SYS_MENU.field("id", Long.class).in(menuIds)))
                    .forEach(m -> {
                        if (m.getPermissionCode() != null && !m.getPermissionCode().isBlank()) {
                            permissions.add(m.getPermissionCode());
                        }
                    });
        }
        return permissions;
    }

    public Set<String> getRoleCodes(Long userId) {
        Set<Long> roleIds = roleIdsOf(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE, SysRoleDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ROLE),
                                JooqTables.SYS_ROLE.field("id", Long.class).in(roleIds)))
                .stream().map(SysRoleDO::getRoleCode).collect(Collectors.toSet());
    }

    private Set<Long> roleIdsOf(Long userId) {
        // V6 收敛：用户仅绑定单个角色，直接读 sys_user.role_id，不再走 sys_user_role 关联表。
        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, userId);
        if (user == null || user.getRoleId() == null) {
            return Set.of();
        }
        return Set.of(user.getRoleId());
    }
}
