package com.acme.scaffold.system.tenant;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.common.tenant.TenantRules;
import com.acme.scaffold.security.password.PasswordPolicy;

import java.util.ArrayList;
import java.util.List;

/**
 * 租户开通纯函数规则（M19 SaaS 开通 / 平台开通闭环）。
 *
 * <p>无副作用、不触库、可直接离线单测；供 {@link TenantProvisioningService} 在「写库前」做全额结构校验，
 * 复用 {@link TenantRules}（联系人格式）与 {@link PasswordPolicy}（密码复杂度），保证与用户/租户创建同源口径。</p>
 */
public final class TenantProvisioningRules {

    /** 租户管理员角色编码前缀：前缀 + 大写租户编码，保证全局唯一（避开 RoleService 的角色码全局唯一约束）。 */
    public static final String ADMIN_ROLE_PREFIX = "TENANT_ADMIN_";

    /** 默认部门编码前缀。 */
    public static final String ADMIN_DEPT_PREFIX = "DEPT_";

    /** 管理员用户名缺省值。 */
    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    private TenantProvisioningRules() {
    }

    /** 派生租户管理员角色编码（全局唯一）。 */
    public static String deriveRoleCode(String tenantCode) {
        return ADMIN_ROLE_PREFIX + (tenantCode == null ? "" : tenantCode.toUpperCase());
    }

    /** 派生默认部门编码。 */
    public static String deriveDeptCode(String tenantCode) {
        return ADMIN_DEPT_PREFIX + (tenantCode == null ? "" : tenantCode.toUpperCase());
    }

    /**
     * 校验开通请求的管理员信息（租户本身由 {@link TenantRules} 负责）。
     * 返回错误列表，空列表表示通过。密码复杂度由 {@link PasswordPolicy#validate} 兜底（不通过抛异常，被捕获为可读错误）。
     */
    public static List<String> validate(String adminUsername, String adminPassword,
                                       String adminDisplayName, String adminMobile, String adminEmail) {
        List<String> errors = new ArrayList<>();
        String username = (adminUsername == null || adminUsername.isBlank())
                ? DEFAULT_ADMIN_USERNAME : adminUsername.trim();
        if (username.length() < 2 || username.length() > 64) {
            errors.add("管理员用户名长度须为 2–64 字符");
        }
        if (adminDisplayName == null || adminDisplayName.isBlank()) {
            errors.add("管理员显示名不能为空");
        }
        if (adminPassword == null || adminPassword.length() < 8) {
            errors.add("管理员初始密码至少 8 位");
        }
        if (adminMobile != null && !adminMobile.isBlank() && !TenantRules.isValidContactPhone(adminMobile)) {
            errors.add("管理员电话格式非法（仅含数字/空格/连字符/加号，5–20 位）");
        }
        if (adminEmail != null && !adminEmail.isBlank() && !TenantRules.isValidContactEmail(adminEmail)) {
            errors.add("管理员邮箱格式非法");
        }
        if (!errors.isEmpty()) {
            return errors;
        }
        // 密码复杂度策略（与用户创建同源）；失败抛 BusinessException，统一转为可读错误
        try {
            PasswordPolicy.validate(adminPassword);
        } catch (BusinessException e) {
            errors.add(e.getMessage());
        }
        return errors;
    }
}
