package com.acme.scaffold.system.role.application;
import com.acme.scaffold.common.error.CommonErrorCode;

import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.FetchSupport;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.system.role.domain.model.SysRoleDO;

/**
 * 角色相关共享静态支撑方法。
 *
 * <p>{@code RoleDataScopeService} 与 {@code RoleColumnPermissionService} 原本各自内联了一份完全相同的
 * 「校验角色存在、不存在抛 {@link CommonErrorCode#NOT_FOUND}」逻辑（均针对 {@code sys_role} 表）；
 * 此处收敛为单一事实来源，两服务保留同名私有方法作为委托壳。</p>
 */
final class RoleSupport {

    private RoleSupport() {
    }

    /** 校验角色存在，返回角色 ID；不存在抛 {@link CommonErrorCode#NOT_FOUND}。 */
    static Long requireRole(DSLContext dsl, Long roleId) {
        SysRoleDO role = FetchSupport.requirePresent(SqlWriters.fetchById(dsl, SqlTables.SYS_ROLE, SysRoleDO.class, roleId), "角色不存在");
        return roleId;
    }
}
