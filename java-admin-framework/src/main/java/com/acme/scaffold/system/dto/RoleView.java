package com.acme.scaffold.system.dto;

import java.util.Set;

public record RoleView(Long id, String roleCode, String roleName, String roleType, String status,
                       Integer sortNo, Set<Long> menuIds, Set<Long> apiIds) {

    public static RoleView from(com.acme.scaffold.system.entity.SysRoleDO r) {
        return new RoleView(r.getId(), r.getRoleCode(), r.getRoleName(), r.getRoleType(), r.getStatus(),
                r.getSortNo(), Set.of(), Set.of());
    }
}
