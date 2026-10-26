package com.acme.scaffold.common.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantRules} 新增校验能力（M19 P2）离线单测：状态归一/白名单、联系人电话/邮箱格式、
 * 全字段 {@code validateTenant} 重载。P1 已覆盖租户 ID 解析与编码/名称基础校验，此处覆盖 P2 增量。
 */
class TenantRulesValidationTest {

    // ------------------------------------------------------------------
    // 状态归一 / 白名单
    // ------------------------------------------------------------------

    @Test
    @DisplayName("normalizeTenantStatus：白名单内大小写归一为规范大写")
    void normalizeStatus_canonicalizes() {
        assertEquals("ENABLED", TenantRules.normalizeTenantStatus("enabled"));
        assertEquals("DISABLED", TenantRules.normalizeTenantStatus("Disabled"));
        assertEquals("ENABLED", TenantRules.normalizeTenantStatus("ENABLED"));
    }

    @Test
    @DisplayName("normalizeTenantStatus：空值/非法值返回 null")
    void normalizeStatus_rejectsInvalid() {
        assertNull(TenantRules.normalizeTenantStatus(null));
        assertNull(TenantRules.normalizeTenantStatus("  "));
        assertNull(TenantRules.normalizeTenantStatus("PAUSED"));
    }

    // ------------------------------------------------------------------
    // 联系人格式
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isValidContactName：1–64 字符合法，空/超长非法")
    void contactName_bounds() {
        assertTrue(TenantRules.isValidContactName("张三"));
        assertTrue(TenantRules.isValidContactName("a".repeat(64)));
        assertFalse(TenantRules.isValidContactName(null));
        assertFalse(TenantRules.isValidContactName(""));
        assertFalse(TenantRules.isValidContactName("a".repeat(65)));
    }

    @Test
    @DisplayName("isValidContactPhone：空串/ null 视为未填（合法）；非空须匹配格式")
    void contactPhone_format() {
        assertTrue(TenantRules.isValidContactPhone(null));
        assertTrue(TenantRules.isValidContactPhone(""));
        assertTrue(TenantRules.isValidContactPhone("13800138000"));
        assertTrue(TenantRules.isValidContactPhone("+86 138-0013-8000"));
        assertFalse(TenantRules.isValidContactPhone("abc"));
        assertFalse(TenantRules.isValidContactPhone("12"));
    }

    @Test
    @DisplayName("isValidContactEmail：空串/ null 视为未填（合法）；非空须匹配格式")
    void contactEmail_format() {
        assertTrue(TenantRules.isValidContactEmail(null));
        assertTrue(TenantRules.isValidContactEmail(""));
        assertTrue(TenantRules.isValidContactEmail("ops@example.com"));
        assertFalse(TenantRules.isValidContactEmail("not-an-email"));
        assertFalse(TenantRules.isValidContactEmail("a@b"));
    }

    // ------------------------------------------------------------------
    // 全字段 validateTenant 重载
    // ------------------------------------------------------------------

    @Test
    @DisplayName("validateTenant 重载：合法全字段返回空错误列表")
    void validateTenant_fullValid() {
        List<String> errors = TenantRules.validateTenant("ACME01", "示例租户",
                "ENABLED", "13800138000", "ops@example.com",
                LocalDateTime.now().plusDays(1));
        assertTrue(errors.isEmpty(), "期望无错误，实际=" + errors);
    }

    @Test
    @DisplayName("validateTenant 重载：编码/状态/电话/邮箱/过期 五类错误均被捕获")
    void validateTenant_fullInvalid() {
        List<String> errors = TenantRules.validateTenant("bad", "示例租户", "PAUSED",
                "abc", "bad", LocalDateTime.now().minusDays(1));
        assertEquals(5, errors.size(), "期望 5 处错误，实际=" + errors);
        assertTrue(errors.stream().anyMatch(e -> e.contains("租户编码")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("状态非法")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("电话")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("邮箱")));
        assertTrue(errors.stream().anyMatch(e -> e.contains("过期时间")));
    }

    @Test
    @DisplayName("validateTenant 重载：空状态回落默认，不报错")
    void validateTenant_blankStatusFallsBack() {
        List<String> errors = TenantRules.validateTenant("ACME01", "示例租户",
                null, null, null, null);
        assertTrue(errors.isEmpty(), "空状态应回落默认 ENABLED，实际=" + errors);
    }
}
