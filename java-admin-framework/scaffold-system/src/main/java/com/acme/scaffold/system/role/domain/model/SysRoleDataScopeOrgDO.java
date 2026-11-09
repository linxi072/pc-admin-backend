package com.acme.scaffold.system.role.domain.model;

import lombok.Data;

@Data
public class SysRoleDataScopeOrgDO {

    private Long id;

    private Long tenantId = 0L;
    private Long ruleId;
    private Long orgId;
}
