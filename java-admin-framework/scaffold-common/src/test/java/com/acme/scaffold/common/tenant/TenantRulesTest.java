package com.acme.scaffold.common.tenant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TenantRules} 离线单测：租户 ID 校验/归一、平台租户判定、编码/名称结构校验。
 * 全部为纯函数断言，不依赖 Spring 上下文与数据库。
 */
class TenantRulesTest {

    // ------------------------------------------------------------------
    // 租户 ID 校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isValidTenantId：非 null 且 >=0 合法（0 为平台租户）")
    void isValidTenantId() {
        assertTrue(TenantRules.isValidTenantId(0L));
        assertTrue(TenantRules.isValidTenantId(1L));
        assertTrue(TenantRules.isValidTenantId(999L));
        assertFalse(TenantRules.isValidTenantId(-1L));
        assertFalse(TenantRules.isValidTenantId(null));
    }

    @Test
    @DisplayName("isPlatformTenant：仅 id==0 命中")
    void isPlatformTenant() {
        assertTrue(TenantRules.isPlatformTenant(0L));
        assertFalse(TenantRules.isPlatformTenant(1L));
        assertFalse(TenantRules.isPlatformTenant(null));
    }

    // ------------------------------------------------------------------
    // 归一
    // ------------------------------------------------------------------

    @Test
    @DisplayName("normalizeTenantId：Number/字符串可解析，负数/非数字/空返回 null")
    void normalizeTenantId() {
        assertEquals(7L, TenantRules.normalizeTenantId(7));
        assertEquals(7L, TenantRules.normalizeTenantId(7L));
        assertEquals(8L, TenantRules.normalizeTenantId("8"));
        assertEquals(9L, TenantRules.normalizeTenantId(" 9 "));
        assertEquals(0L, TenantRules.normalizeTenantId(0));

        assertNull(TenantRules.normalizeTenantId(null));
        assertNull(TenantRules.normalizeTenantId(""));
        assertNull(TenantRules.normalizeTenantId("   "));
        assertNull(TenantRules.normalizeTenantId("-1"));
        assertNull(TenantRules.normalizeTenantId("abc"));
        assertNull(TenantRules.normalizeTenantId(true));   // Boolean 非 Number
    }

    // ------------------------------------------------------------------
    // 编码/名称结构校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("isValidTenantCode：大写字母/数字/下划线/连字符，3–64 位")
    void isValidTenantCode() {
        assertTrue(TenantRules.isValidTenantCode("ACME"));
        assertTrue(TenantRules.isValidTenantCode("TENANT_01"));
        assertTrue(TenantRules.isValidTenantCode("ORG-A"));
        assertFalse(TenantRules.isValidTenantCode("AB"));       // 过短
        assertFalse(TenantRules.isValidTenantCode("abc"));      // 小写
        assertFalse(TenantRules.isValidTenantCode("A B"));      // 含空格
        assertFalse(TenantRules.isValidTenantCode(null));
    }

    @Test
    @DisplayName("isValidTenantName：trim 后 1–128 字符")
    void isValidTenantName() {
        assertTrue(TenantRules.isValidTenantName("Acme 租户"));
        assertTrue(TenantRules.isValidTenantName(" A "));
        assertFalse(TenantRules.isValidTenantName(""));
        assertFalse(TenantRules.isValidTenantName("   "));
        assertFalse(TenantRules.isValidTenantName(null));
        assertFalse(TenantRules.isValidTenantName("X".repeat(129)));
    }

    @Test
    @DisplayName("validateTenant：编码/名称同时非法时错误可累积")
    void validateTenant_accumulates() {
        List<String> errors = TenantRules.validateTenant("ab", "");
        assertEquals(2, errors.size());

        assertTrue(TenantRules.validateTenant("ACME", "Acme 租户").isEmpty());
    }
}
