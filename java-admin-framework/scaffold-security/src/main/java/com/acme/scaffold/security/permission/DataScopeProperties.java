package com.acme.scaffold.security.permission;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据权限兜底策略配置（P0-1）。
 *
 * <p>绑定 {@code app.datascope.default-policy}：取值 {@code ALL} / {@code SELF}，
 * 默认 {@code SELF}（fail-closed，配置缺失即最小可见）。</p>
 *
 * <p>作为 {@code @Component} 注册，由 Spring Boot 自动绑定 {@code @ConfigurationProperties}；
 * 未配置时回落到 {@link DataScopeDefaultPolicy#SELF}。</p>
 *
 * <p>超管放行（D1 修复）：{@code app.datascope.super-admin-bypass}（默认 {@code true}）与
 * {@code app.datascope.super-admin-role-code}（默认 {@code SUPER_ADMIN}）。种子数据中各资源
 * 均未预置 {@code sys_role_data_scope} 规则，若不放行则默认策略 SELF 会让超管"只看得见自己"，
 * 新建用户/部门/公告在列表与详情中全部不可见（表现为 200 建成功、随后 GET 404）。
 * 超管本身已持有全部权限码，放行不构成越权扩权；如需强制超管也走规则解析，显式设为 {@code false}。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.datascope")
public class DataScopeProperties {

    private DataScopeDefaultPolicy defaultPolicy = DataScopeDefaultPolicy.SELF;

    /** 是否为超级管理员角色放行数据范围（true=不限可见，默认 true）。 */
    private boolean superAdminBypass = true;

    /** 超级管理员角色码（role_code），与 sys_role.role_code 对应。 */
    private String superAdminRoleCode = "SUPER_ADMIN";

    public DataScopeDefaultPolicy getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(DataScopeDefaultPolicy defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

    public boolean isSuperAdminBypass() {
        return superAdminBypass;
    }

    public void setSuperAdminBypass(boolean superAdminBypass) {
        this.superAdminBypass = superAdminBypass;
    }

    public String getSuperAdminRoleCode() {
        return superAdminRoleCode;
    }

    public void setSuperAdminRoleCode(String superAdminRoleCode) {
        this.superAdminRoleCode = superAdminRoleCode;
    }
}
