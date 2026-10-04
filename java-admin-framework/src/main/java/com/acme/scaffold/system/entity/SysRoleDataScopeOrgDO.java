package com.acme.scaffold.system.entity;

import lombok.Data;

@Data
public class SysRoleDataScopeOrgDO {

    private Long id;

    private Long tenantId = 0L;
    private Long ruleId;
    private Long orgId;
}
