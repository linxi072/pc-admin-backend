package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysUserDO {

    private Long id;

    private Long tenantId = 0L;
    private String username;
    private String passwordHash;
    private String displayName;
    private String mobile;
    private String email;
    private Long primaryOrgId;
    private String status;
    private Integer failedLoginCount = 0;
    private LocalDateTime lockedUntil;
    private LocalDateTime passwordChangedAt;
    private Integer passwordExpired = 0;
    private Integer tokenVersion = 1;
    private Integer mfaEnabled = 0;
    private LocalDateTime lastLoginAt;

    private Integer version = 0;

    private Integer deleted = 0;

    private Long createdBy;
    private LocalDateTime createdAt;
    private Long updatedBy;
    private LocalDateTime updatedAt;
}
