package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysUserRoleDO {

    private Long id;

    private Long tenantId = 0L;
    private Long userId;
    private Long roleId;
    private Long scopeOrgId = 0L;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private LocalDateTime createdAt;
}
