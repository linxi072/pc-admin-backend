package com.acme.scaffold.system.permission.adapter;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.permission.PermissionDataPort;
import com.acme.scaffold.system.entity.SysApiResourceDO;
import com.acme.scaffold.system.entity.SysMenuDO;
import com.acme.scaffold.system.entity.SysRoleApiDO;
import com.acme.scaffold.system.entity.SysRoleDO;
import com.acme.scaffold.system.entity.SysRoleMenuDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link PermissionDataPort} 的 system 侧适配器实现。
 *
 * <p>承载原 {@code PermissionService} 中全部 jOOQ 直查逻辑（B1 将其从 security.permission 包剥离）。
 * 本类位于 {@code system} 包，允许 import {@code system.entity}，满足模块边界：
 * security.permission 不再直连系统域持久化对象。</p>
 */
@Component
@RequiredArgsConstructor
public class PermissionDataAdapter implements PermissionDataPort {

    private static final JooqTables.TableRef ROLE = JooqTables.SYS_ROLE;

    private final DSLContext dsl;

    private static final JooqTables.TableRef AR = JooqTables.SYS_API_RESOURCE;

    @Override
    public Set<Long> roleIdsOfUser(Long userId) {
        return UserRoleSupport.roleIdsOfUser(dsl, userId);
    }

    @Override
    public Set<String> permissionCodesOfRoles(Set<Long> roleIds) {
        Set<String> permissions = new HashSet<>();
        if (roleIds.isEmpty()) {
            return permissions;
        }

        List<Long> apiIds = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_API, SysRoleApiDO.class,
                        JooqTables.SYS_ROLE_API.field("role_id", Long.class).in(roleIds)).stream()
                .map(SysRoleApiDO::getApiId).collect(Collectors.toList());
        if (!apiIds.isEmpty()) {
            JooqWriters.fetchList(dsl, AR, SysApiResourceDO.class,
                            DSL.and(JooqWriters.notDeleted(AR),
                                    AR.field("id", Long.class).in(apiIds)))
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

    @Override
    public Set<String> roleCodesOfRoles(Set<Long> roleIds) {
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return JooqWriters.fetchList(dsl, ROLE, SysRoleDO.class,
                        DSL.and(JooqWriters.notDeleted(ROLE),
                                ROLE.field("id", Long.class).in(roleIds)))
                .stream().map(SysRoleDO::getRoleCode).collect(Collectors.toSet());
    }
}
