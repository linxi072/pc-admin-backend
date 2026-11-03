package com.acme.scaffold.config.multiregion;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link TenantUnitMapping} 纯逻辑单测：覆盖显式映射、租户组区间、默认回落与优先级。
 */
class TenantUnitMappingTest {

    private TenantUnitMapping build() {
        MultiRegionProperties props = new MultiRegionProperties();
        props.setDefaultUnit("primary");
        props.setTenantToUnit(Map.of(10L, "unit-b"));
        props.setTenantGroups(List.of(new TenantGroupRange(1000, 1999, "unit-east")));
        return TenantUnitMapping.from(props, "primary");
    }

    @Test
    void explicitMappingTakesPrecedence() {
        TenantUnitMapping m = build();
        assertEquals("unit-b", m.resolve(10));
    }

    @Test
    void groupRangeMappingInclusiveEndpoints() {
        TenantUnitMapping m = build();
        assertEquals("unit-east", m.resolve(1500));
        assertEquals("unit-east", m.resolve(1000));
        assertEquals("unit-east", m.resolve(1999));
    }

    @Test
    void defaultUnitWhenNoMatch() {
        TenantUnitMapping m = build();
        assertEquals("primary", m.resolve(5));
        assertEquals("primary", m.resolve(999));
        assertEquals("primary", m.resolve(2000));
    }

    @Test
    void explicitBeatsGroupRange() {
        MultiRegionProperties props = new MultiRegionProperties();
        props.setDefaultUnit("primary");
        props.setTenantToUnit(Map.of(1500L, "unit-vip"));
        props.setTenantGroups(List.of(new TenantGroupRange(1000, 1999, "unit-east")));
        TenantUnitMapping m = TenantUnitMapping.from(props, "primary");
        assertEquals("unit-vip", m.resolve(1500));
    }

    @Test
    void defaultUnitFallsBackToPrimaryWhenBlank() {
        MultiRegionProperties props = new MultiRegionProperties();
        props.setDefaultUnit("");
        props.setTenantToUnit(Map.of());
        props.setTenantGroups(List.of());
        TenantUnitMapping m = TenantUnitMapping.from(props, "primary");
        assertEquals("primary", m.defaultUnitName());
        assertEquals("primary", m.resolve(42));
    }
}
