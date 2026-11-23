package com.acme.scaffold.system.menu.domain.rule;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 菜单类型取值域纯规则单测（TEST-COVERAGE-PLAN 第二批 / T3 system 主链路）。
 * 锁定「后端全名 + 前端单字母」双字面量兼容契约（若依对齐 P0-2），防止 normalize 破坏前端显示。
 */
class MenuTypeRulesTest {

    @Test
    void shouldAcceptBackendFullNames() {
        assertTrue(MenuTypeRules.isValid("CATALOG"));
        assertTrue(MenuTypeRules.isValid("MENU"));
        assertTrue(MenuTypeRules.isValid("BUTTON"));
    }

    @Test
    void shouldAcceptFrontendSingleLetters() {
        assertTrue(MenuTypeRules.isValid("C"));
        assertTrue(MenuTypeRules.isValid("M"));
        assertTrue(MenuTypeRules.isValid("B"));
    }

    @Test
    void shouldRejectNullBlankAndUnknown() {
        assertFalse(MenuTypeRules.isValid(null));
        assertFalse(MenuTypeRules.isValid(""));
        assertFalse(MenuTypeRules.isValid("   "));
        assertFalse(MenuTypeRules.isValid("menu"), "大小写敏感，小写不合法");
        assertFalse(MenuTypeRules.isValid("catalog"));
        assertFalse(MenuTypeRules.isValid("UNKNOWN"));
    }

    @Test
    void allowedDescriptionShouldListBothSchemas() {
        assertTrue(MenuTypeRules.allowedDescription().contains("CATALOG"));
        assertTrue(MenuTypeRules.allowedDescription().contains("C/M/B"));
    }
}
