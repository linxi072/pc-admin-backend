package com.acme.scaffold.system.post.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 岗位。
 * <p>编码在租户内唯一；软删除 + 乐观锁（version）范式对齐 sys_role。
 * 表名 / 列名与 {@code com.acme.scaffold.mybatis.dsl.SqlTables.SYS_POST} 严格一致。
 */
@Data
public class SysPostDO {

    private Long id;

    private Long tenantId = 0L;
    private String postCode;
    private String postName;
    private Integer postSort = 0;
    private String status = "ACTIVE";
    private String remark;
    private Integer version = 0;
    private Integer deleted = 0;
    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
