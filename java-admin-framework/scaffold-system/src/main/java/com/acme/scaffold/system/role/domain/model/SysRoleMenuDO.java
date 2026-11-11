package com.acme.scaffold.system.role.domain.model;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class SysRoleMenuDO {

    private Long id;

    private Long tenantId = 0L;
    private Long roleId;
    private Long menuId;
    private LocalDateTime createdAt;
}
