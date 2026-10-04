package com.acme.scaffold.security.permission;

/**
 * 数据权限范围类型。预留扩展点，业务查询可据此拼接机构过滤条件。
 */
public enum DataScopeType {

    /** 全部数据，不限制。 */
    ALL,
    /** 仅本人数据（按 operatorId 过滤）。 */
    SELF,
    /** 本机构数据。 */
    DEPT,
    /** 本机构及下属机构数据。 */
    DEPT_AND_CHILD,
    /** 自定义机构集合（来自 sys_role_data_scope_org）。 */
    CUSTOM
}
