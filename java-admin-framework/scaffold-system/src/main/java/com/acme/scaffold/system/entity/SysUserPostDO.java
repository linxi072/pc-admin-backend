package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-岗位关联（多对多）。
 * <p>一个用户可兼任多个岗位；无 deleted 列，用户保存岗位时为整体替换（物理删+插）。
 * 表名 / 列名与 {@code com.acme.scaffold.jooq.dsl.JooqTables.SYS_USER_POST} 严格一致。
 */
@Data
public class SysUserPostDO {

    private Long id;

    private Long tenantId = 0L;
    private Long userId;
    private Long postId;
    private LocalDateTime createdAt;
}
