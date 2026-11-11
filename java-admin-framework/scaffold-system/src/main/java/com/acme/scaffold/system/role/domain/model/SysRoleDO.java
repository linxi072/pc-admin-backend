package com.acme.scaffold.system.role.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SysRoleDO {

    private Long id;

    private Long tenantId = 0L;
    private String roleCode;
    private String roleName;
    private String roleType = "BUSINESS";
    private String status = "ACTIVE";
    private Integer sortNo = 0;

    private Integer version = 0;

    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
