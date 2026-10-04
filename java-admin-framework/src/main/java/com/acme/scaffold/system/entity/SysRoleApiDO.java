package com.acme.scaffold.system.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysRoleApiDO {

    private Long id;

    private Long tenantId = 0L;
    private Long roleId;
    private Long apiId;
    private LocalDateTime createdAt;
}
