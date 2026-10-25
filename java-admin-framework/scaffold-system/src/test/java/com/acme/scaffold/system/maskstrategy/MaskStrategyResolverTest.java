package com.acme.scaffold.system.maskstrategy;

import com.acme.scaffold.system.maskstrategy.domain.SysMaskStrategyDO;
import com.acme.scaffold.system.maskstrategy.rules.MaskStrategyRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MaskStrategyResolver} 离线单测：未命中安全回落、作用范围三态、角色豁免、优先级命中
 * 与 null 防御。策略列表经 mock Service 注入，实际脱敏委托真实 {@code SensitiveMasker}
 * （与 M1 审计落库、M20 审计增强共用的脱敏内核），端到端覆盖「命中→裁决→掩码」。
 */
class MaskStrategyResolverTest {

    private final MaskStrategyService service = mock(MaskStrategyService.class);
    private final MaskStrategyResolver resolver = new MaskStrategyResolver(service);

    private void givenStrategies(SysMaskStrategyDO... strategies) {
        when(service.activeStrategies(anyLong())).thenReturn(List.of(strategies));
    }

    @Test
    @DisplayName("未命中策略：按调用方声明的 fallbackType 默认掩码（安全回落，与既有口径一致）")
    void resolve_noStrategyFallsBackToDefault() {
        givenStrategies(strategy(1L, "other.field", "MOBILE", "ALL", null, 0, 1));

        // 声明的 fallback 为 MOBILE：即便未命中，也按手机号默认脱敏
        assertEquals("138****5678", resolver.resolve(0L, "sys_user.mobile",
                "13812345678", com.acme.scaffold.common.sensitive.SensitiveType.MOBILE, Set.of()));
    }

    @Test
    @DisplayName("未命中策略且 fallback=NONE：原样返回（与既有『本就不脱敏』语义一致）")
    void resolve_noStrategyNoneFallbackReturnsPlain() {
        givenStrategies();
        assertEquals("13812345678", resolver.resolve(0L, "sys_user.mobile",
                "13812345678", com.acme.scaffold.common.sensitive.SensitiveType.NONE, Set.of()));
    }

    @Test
    @DisplayName("命中 ALL：一律脱敏（即便持有特权角色也不放行，导出离开边界场景）")
    void resolve_scopeAllAlwaysMasks() {
        givenStrategies(strategy(1L, "sys_user.mobile", "MOBILE", "ALL", null, 0, 1));
        assertEquals("138****5678", resolver.resolve(0L, "sys_user.mobile",
                "13812345678", com.acme.scaffold.common.sensitive.SensitiveType.NONE, Set.of("SUPER_ADMIN")));
    }

    @Test
    @DisplayName("命中 NONE：永不脱敏（显式放行，用于已脱敏存储字段）")
    void resolve_scopeNoneReturnsPlain() {
        givenStrategies(strategy(1L, "sys_user.mobile", "MOBILE", "NONE", null, 0, 1));
        assertEquals("13812345678", resolver.resolve(0L, "sys_user.mobile",
                "13812345678", com.acme.scaffold.common.sensitive.SensitiveType.MOBILE, Set.of()));
    }

    @Test
    @DisplayName("命中 ROLE_EXEMPT：豁免角色看明文，非豁免角色脱敏")
    void resolve_roleExempt() {
        givenStrategies(strategy(1L, "sys_user.mobile", "MOBILE", "ROLE_EXEMPT", "SUPER_ADMIN", 0, 1));
        assertEquals("13812345678", resolver.resolve(0L, "sys_user.mobile",
                "13812345678", com.acme.scaffold.common.sensitive.SensitiveType.MOBILE, Set.of("SUPER_ADMIN")));
        assertEquals("138****5678", resolver.resolve(0L, "sys_user.mobile",
                "13812345678", com.acme.scaffold.common.sensitive.SensitiveType.NONE, Set.of("OPERATOR")));
    }

    @Test
    @DisplayName("同字段多条策略：优先级最高者胜（决定算法与范围）")
    void resolve_highestPriorityWins() {
        givenStrategies(
                strategy(1L, "audit.ip", "NONE", "NONE", null, 0, 1),       // 低优先级：放行
                strategy(2L, "audit.ip", "IP", "ALL", null, 9, 1));        // 高优先级：一律脱敏
        // 高优先级命中 → 按 IP 脱敏（保留前两段）
        assertEquals("192.168.*.*", resolver.resolve(0L, "audit.ip",
                "192.168.1.100", com.acme.scaffold.common.sensitive.SensitiveType.NONE, Set.of()));
    }

    @Test
    @DisplayName("null 值原样返回（不触策略、不抛异常）")
    void resolve_nullValue() {
        givenStrategies(strategy(1L, "sys_user.mobile", "MOBILE", "ALL", null, 0, 1));
        assertNull(resolver.resolve(0L, "sys_user.mobile",
                null, com.acme.scaffold.common.sensitive.SensitiveType.MOBILE, Set.of()));
    }

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
