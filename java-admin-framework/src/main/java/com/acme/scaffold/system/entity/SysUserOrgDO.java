package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-部门关联（多对多）。
 * <p>回退 V6 单值收敛：一个用户可归属多个部门，{@code is_primary=1} 标记主部门。
 * 部门在框架内称「机构」（sys_org），故关联表名为 sys_user_org。
 * 表名 / 列名与 {@code com.acme.scaffold.jooq.JooqTables.SYS_USER_ORG} 严格一致。
 */
@Data
public class SysUserOrgDO {

    private Long id;

    private Long tenantId = 0L;
    private Long userId;
    private Long orgId;
    private Integer isPrimary = 0;
    private LocalDateTime createdAt;
}
