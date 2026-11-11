package com.acme.scaffold.system.role.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

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
