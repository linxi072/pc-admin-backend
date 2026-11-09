package com.acme.scaffold.system.permission.adapter;

import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.user.domain.model.SysUserRoleDO;
import com.acme.scaffold.mybatis.dsl.DSLContext;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户-角色关联查询共享静态支撑方法。
 *
 * <p>{@code PermissionDataAdapter} 与 {@code DataScopeDataAdapter} 原本各自内联了一份完全相同的
 * 「从 {@code sys_user_role} 取某用户全部角色 id」逻辑；此处收敛为单一事实来源，
 * 两适配器保留 {@code @Override roleIdsOfUser} 作为委托壳（端口契约不变）。</p>
 */
final class UserRoleSupport {

    private UserRoleSupport() {
    }

    /** 多对多：从 {@code sys_user_role} 关联表取该用户全部角色 id（并集）。 */
    static Set<Long> roleIdsOfUser(DSLContext dsl, Long userId) {
        List<SysUserRoleDO> rows = SqlWriters.fetchList(dsl, SqlTables.SYS_USER_ROLE, SysUserRoleDO.class,
                SqlTables.SYS_USER_ROLE.field("user_id", Long.class).eq(userId));
        if (rows.isEmpty()) {
            return Set.of();
        }
        return rows.stream().map(SysUserRoleDO::getRoleId).collect(Collectors.toSet());
    }
}
