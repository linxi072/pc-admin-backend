package com.acme.scaffold.system.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantProvisioningRules} 离线单测：租户开通的纯函数规则（编码派生 + 管理员信息结构校验）。
 *
 * <p>与 {@link TenantServiceTest} 同一约定：仅固化不触库的校验语义，DB 交互（创建部门/角色/用户）由
 * MySQL 集成测试覆盖。密码复杂度复用 {@code PasswordPolicy.validate}（失败被捕获为可读错误）。</p>
 */
class TenantProvisioningRulesTest {

    @Test
    @DisplayName("deriveRoleCode / deriveDeptCode：前缀 + 大写租户编码，保证全局唯一")
    void deriveCodes_usePrefixAndUppercaseTenantCode() {
        assertEquals("TENANT_ADMIN_ACME01", TenantProvisioningRules.deriveRoleCode("acme01"));
        assertEquals("DEPT_ACME01", TenantProvisioningRules.deriveDeptCode("acme01"));
        assertEquals("TENANT_ADMIN_", TenantProvisioningRules.deriveRoleCode(null));
        assertEquals("TENANT_ADMIN_", TenantProvisioningRules.deriveRoleCode(""));
    }

    @Test
    @DisplayName("validate：全部合法 → 空错误列表")
    void validate_allValid_returnsEmpty() {
        List<String> errors = TenantProvisioningRules.validate(
                "admin", "Admin@123456", "张三", "13800000000", "admin@example.com");
        assertTrue(errors.isEmpty(), "期望无错误，实际：" + errors);
    }

    @Test
    @DisplayName("validate：用户名过短被拒")
    void validate_shortUsername_reportsError() {
        List<String> errors = TenantProvisioningRules.validate(
                "a", "Admin@123456", "张三", null, null);
        assertTrue(errors.stream().anyMatch(e -> e.contains("用户名")), errors.toString());
    }

    @Test
    @DisplayName("validate：显示名为空被拒")
    void validate_blankDisplayName_reportsError() {
        List<String> errors = TenantProvisioningRules.validate(
                "admin", "Admin@123456", "", null, null);
        assertTrue(errors.stream().anyMatch(e -> e.contains("显示名")), errors.toString());
    }

    @Test
    @DisplayName("validate：密码过短被拒")
    void validate_shortPassword_reportsError() {
        List<String> errors = TenantProvisioningRules.validate(
                "admin", "short", "张三", null, null);
        assertTrue(errors.stream().anyMatch(e -> e.contains("密码")), errors.toString());
    }

    @Test
    @DisplayName("validate：密码缺少复杂度（仅小写）被拒")
    void validate_weakPassword_reportsError() {
        List<String> errors = TenantProvisioningRules.validate(
                "admin", "password", "张三", null, null);
        assertFalse(errors.isEmpty(), "弱密码应被 PasswordPolicy 捕获为错误");
        assertTrue(errors.stream().anyMatch(e -> e.contains("密码")), errors.toString());
    }

    @Test
    @DisplayName("validate：电话格式非法被拒")
    void validate_invalidPhone_reportsError() {
        List<String> errors = TenantProvisioningRules.validate(
                "admin", "Admin@123456", "张三", "abc", null);
        assertTrue(errors.stream().anyMatch(e -> e.contains("电话")), errors.toString());
    }

    @Test
    @DisplayName("validate：邮箱格式非法被拒")
    void validate_invalidEmail_reportsError() {
        List<String> errors = TenantProvisioningRules.validate(
                "admin", "Admin@123456", "张三", null, "not-an-email");
        assertTrue(errors.stream().anyMatch(e -> e.contains("邮箱")), errors.toString());
    }

    @Test
    @DisplayName("validate：用户名为空 → 回落缺省 admin 且通过用户名长度校验")
    void validate_blankUsername_defaultsToAdmin() {
        List<String> errors = TenantProvisioningRules.validate(
                "", "Admin@123456", "张三", null, null);
        // 回落“admin”后不应再有用户名相关错误；整体应通过
        assertTrue(errors.isEmpty(), "缺省 admin 后期望无错误，实际：" + errors);
        assertEquals("admin", TenantProvisioningRules.DEFAULT_ADMIN_USERNAME);
    }
}
