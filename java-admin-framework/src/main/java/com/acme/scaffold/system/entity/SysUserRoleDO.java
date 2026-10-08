package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-角色关联（多对多）。
 * <p>回退 V6 单值收敛：一个用户可绑定多个角色，{@code is_primary=1} 标记主角色。
 * 表名 / 列名与 {@code com.acme.scaffold.jooq.JooqTables.SYS_USER_ROLE} 严格一致。
 */
@Data
public class SysUserRoleDO {

    private Long id;

    private Long tenantId = 0L;
    private Long userId;
    private Long roleId;
    private Integer isPrimary = 0;
    private LocalDateTime createdAt;
}
