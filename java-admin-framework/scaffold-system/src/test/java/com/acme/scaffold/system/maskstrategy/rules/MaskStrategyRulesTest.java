package com.acme.scaffold.system.maskstrategy.rules;

import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.common.sensitive.SensitiveType;
import com.acme.scaffold.system.maskstrategy.domain.SysMaskStrategyDO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MaskStrategyRules} 离线单测：脱敏类型/作用范围归一、豁免角色解析、按字段键命中（优先级）、
 * 角色豁免裁决与结构校验。全部为纯函数断言，不依赖 Spring 上下文与数据库。
 */
class MaskStrategyRulesTest {

    // ------------------------------------------------------------------
    // 归一
    // ------------------------------------------------------------------

    @Test
    @DisplayName("normalizeSensitiveType：合法枚举大小写不敏感，非法/空返回 null")
    void normalizeSensitiveType() {
        assertEquals("MOBILE", MaskStrategyRules.normalizeSensitiveType("mobile"));
        assertEquals("ID_CARD", MaskStrategyRules.normalizeSensitiveType(" ID_CARD "));
        assertEquals("IP", MaskStrategyRules.normalizeSensitiveType("Ip"));
        assertNull(MaskStrategyRules.normalizeSensitiveType("PHONE"));
        assertNull(MaskStrategyRules.normalizeSensitiveType(""));
        assertNull(MaskStrategyRules.normalizeSensitiveType(null));
    }

    @Test
    @DisplayName("normalizeScope：白名单三值大小写不敏感，非法返回 null")
    void normalizeScope() {
        assertEquals("ALL", MaskStrategyRules.normalizeScope("all"));
        assertEquals("ROLE_EXEMPT", MaskStrategyRules.normalizeScope(" role_exempt "));
        assertEquals("NONE", MaskStrategyRules.normalizeScope("NONE"));
        assertNull(MaskStrategyRules.normalizeScope("PARTIAL"));
        assertNull(MaskStrategyRules.normalizeScope(null));
    }

    @Test
    @DisplayName("parseExemptRoles：逗号/分号分隔去空白去重保序，空串返回空集")
    void parseExemptRoles() {
        assertEquals(Set.of("SUPER_ADMIN", "AUDIT_ADMIN"),
                MaskStrategyRules.parseExemptRoles("SUPER_ADMIN, AUDIT_ADMIN ; SUPER_ADMIN"));
        assertEquals(Set.of(), MaskStrategyRules.parseExemptRoles(""));
        assertEquals(Set.of(), MaskStrategyRules.parseExemptRoles(null));
    }

    // ------------------------------------------------------------------
    // 命中与裁决
    // ------------------------------------------------------------------

    @Test
    @DisplayName("selectStrategy：按字段键命中启用项，优先级最高者胜，停用项忽略")
    void selectStrategy_priorityWins() {
        SysMaskStrategyDO low = strategy(1L, "audit.ip", "IP", "ALL", null, 0, 1);
        SysMaskStrategyDO high = strategy(2L, "audit.ip", "CREDENTIAL_TEXT", "ALL", null, 9, 1);
        SysMaskStrategyDO disabled = strategy(3L, "audit.ip", "MOBILE", "ALL", null, 99, 0);
        SysMaskStrategyDO other = strategy(4L, "audit.summary", "CREDENTIAL_TEXT", "ALL", null, 50, 1);

        SysMaskStrategyDO hit = MaskStrategyRules.selectStrategy(
                List.of(low, high, disabled, other), "audit.ip");
        assertEquals(2L, hit.getId());
    }

    @Test
    @DisplayName("selectStrategy：无匹配字段键/全部停用返回 null")
    void selectStrategy_noMatch() {
        SysMaskStrategyDO s = strategy(1L, "audit.ip", "IP", "ALL", null, 0, 1);
        assertNull(MaskStrategyRules.selectStrategy(List.of(s), "audit.summary"));
        s.setEnabled(0);
        assertNull(MaskStrategyRules.selectStrategy(List.of(s), "audit.ip"));
        assertNull(MaskStrategyRules.selectStrategy(null, "audit.ip"));
    }

    @Test
    @DisplayName("shouldMask：ALL 一律脱敏；NONE 永不脱敏")
    void shouldMask_scopeAllAndNone() {
        SysMaskStrategyDO all = strategy(1L, "f", "IP", "ALL", null, 0, 1);
        SysMaskStrategyDO none = strategy(2L, "f", "IP", "NONE", null, 0, 1);
        assertTrue(MaskStrategyRules.shouldMask(all, Set.of("SUPER_ADMIN")));   // ALL → 脱敏
        assertFalse(MaskStrategyRules.shouldMask(none, Set.of()));              // NONE → 放行
    }

    @Test
    @DisplayName("shouldMask：ROLE_EXEMPT 命中豁免角色看明文，否则脱敏；未配豁免兜底脱敏")
    void shouldMask_roleExempt() {
        SysMaskStrategyDO s = strategy(1L, "f", "IP", "ROLE_EXEMPT", "SUPER_ADMIN,AUDIT_ADMIN", 0, 1);
        assertFalse(MaskStrategyRules.shouldMask(s, Set.of("SUPER_ADMIN")));       // 命中豁免
        assertFalse(MaskStrategyRules.shouldMask(s, Set.of("AUDIT_ADMIN", "X")));  // 命中其一即豁免
        assertTrue(MaskStrategyRules.shouldMask(s, Set.of("OPERATOR")));           // 非豁免→脱敏
        assertTrue(MaskStrategyRules.shouldMask(s, Set.of()));                     // 无角色→脱敏

        // 未配置豁免角色：按「脱敏」兜底，避免被误判放行
        SysMaskStrategyDO noExempt = strategy(2L, "f", "IP", "ROLE_EXEMPT", null, 0, 1);
        assertTrue(MaskStrategyRules.shouldMask(noExempt, Set.of("SUPER_ADMIN")));
    }

    @Test
    @DisplayName("decideType：合法类型原样返回，非法回落 NONE")
    void decideType() {
        SysMaskStrategyDO s = strategy(1L, "f", "IP", "ALL", null, 0, 1);
        assertEquals(SensitiveType.IP, MaskStrategyRules.decideType(s));
        s.setSensitiveType("BOGUS");
        assertEquals(SensitiveType.NONE, MaskStrategyRules.decideType(s));
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    @Test
    @DisplayName("validate：合法入参无错误")
    void validate_ok() {
        assertTrue(MaskStrategyRules.validate("USER_MOBILE", "用户手机", "sys_user.mobile",
                "MOBILE", "ROLE_EXEMPT", "SUPER_ADMIN", 0).isEmpty());
    }

    @Test
    @DisplayName("validate：非法脱敏类型/作用范围/优先级/长度均报错且可累积（空豁免不算错）")
    void validate_errors() {
        String long65 = "X".repeat(65);   // 编码上限 64
        String long129 = "Y".repeat(129); // 名称/字段键上限 128
        List<String> errors = MaskStrategyRules.validate(long65, long129, long129,
                "PHONE", "HALF", "x", 10000);
        // 编码过长、名称过长、字段键过长、脱敏类型非法、作用范围非法、优先级越界 = 6
        assertEquals(6, errors.size());
    }

    // ------------------------------------------------------------------
    // 构造辅助
    // ------------------------------------------------------------------

    private static SysMaskStrategyDO strategy(long id, String fieldKey, String type,
                                             String scope, String exempt, int priority, int enabled) {
        SysMaskStrategyDO d = new SysMaskStrategyDO();
        d.setId(id);
        d.setStrategyCode("CODE_" + id);
        d.setFieldKey(fieldKey);
        d.setSensitiveType(type);
        d.setApplyScope(scope);
        d.setExemptRoles(exempt);
        d.setPriority(priority);
        d.setEnabled(enabled);
        return d;
    }
}
