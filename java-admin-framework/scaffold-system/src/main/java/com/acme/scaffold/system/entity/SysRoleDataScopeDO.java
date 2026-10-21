package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysRoleDataScopeDO {

    private Long id;

    private Long tenantId = 0L;
    private Long roleId;
    private String resourceCode;
    private String scopeType;
    private String combineMode = "UNION";

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
