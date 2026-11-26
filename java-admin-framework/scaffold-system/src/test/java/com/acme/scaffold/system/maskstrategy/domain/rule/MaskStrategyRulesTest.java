package com.acme.scaffold.system.maskstrategy.domain.rule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.acme.scaffold.common.sensitive.SensitiveType;
import com.acme.scaffold.system.maskstrategy.domain.model.SysMaskStrategy;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 脱敏策略命中规则单测：平台默认策略合并、租户优先级与类型裁决。 */
class MaskStrategyRulesTest {

    @Test
    void platformDefaultShouldApplyWhenTenantHasNoStrategy() {
        SysMaskStrategy platform = strategy(0L, "MOBILE", 1);

        assertSame(platform, MaskStrategyRules.selectStrategyWithTenantDefault(
                List.of(platform), List.of(), "audit.ip"));
    }

    @Test
    void tenantStrategyShouldWinOnEqualPriority() {
        SysMaskStrategy platform = strategy(0L, "EMAIL", 5);
        SysMaskStrategy tenant = strategy(7L, "MOBILE", 5);

        assertSame(tenant, MaskStrategyRules.selectStrategyWithTenantDefault(
                List.of(platform), List.of(tenant), "audit.ip"));
    }

    @Test
    void higherPriorityShouldStillWinAcrossTenants() {
        SysMaskStrategy platform = strategy(0L, "EMAIL", 10);
        SysMaskStrategy tenant = strategy(7L, "MOBILE", 1);

        assertSame(platform, MaskStrategyRules.selectStrategyWithTenantDefault(
                List.of(platform), List.of(tenant), "audit.ip"));
    }

    @Test
    void invalidTypeShouldDecideNone() {
        SysMaskStrategy bad = strategy(7L, "NOT_A_TYPE", 1);

        assertEquals(SensitiveType.NONE, MaskStrategyRules.decideType(bad));
    }

    private static SysMaskStrategy strategy(long tenantId, String type, int priority) {
        SysMaskStrategy s = new SysMaskStrategy();
        s.setTenantId(tenantId);
        s.setFieldKey("audit.ip");
        s.setSensitiveType(type);
        s.setApplyScope("ALL");
        s.setEnabled(1);
        s.setPriority(priority);
        return s;
    }
}
