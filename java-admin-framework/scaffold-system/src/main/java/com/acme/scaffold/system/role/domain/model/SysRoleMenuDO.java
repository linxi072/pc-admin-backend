package com.acme.scaffold.system.role.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysRoleMenuDO {

    private Long id;

    private Long tenantId = 0L;
    private Long roleId;
    private Long menuId;
    private LocalDateTime createdAt;
}
