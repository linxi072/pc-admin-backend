package com.acme.scaffold.system.maskstrategy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.sensitive.SensitiveMasker;
import com.acme.scaffold.common.sensitive.SensitiveType;
import com.acme.scaffold.system.maskstrategy.domain.model.SysMaskStrategy;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 脱敏策略解析器单测：判定脱敏必须产出掩码，平台默认策略对业务租户生效。 */
class MaskStrategyResolverTest {

    @Test
    void invalidStrategyTypeShouldUseFallbackType() {
        MaskStrategyService service = mock(MaskStrategyService.class);
        when(service.activeStrategies(7L)).thenReturn(List.of(strategy(7L, "BROKEN", "ALL", null, 1)));
        MaskStrategyResolver resolver = new MaskStrategyResolver(service);

        String out = resolver.resolve(7L, "audit.ip", "13812341234", SensitiveType.MOBILE, Set.of());

        assertEquals("138****1234", out);
    }

    @Test
    void invalidStrategyTypeWithoutFallbackShouldUseGenericMask() {
        MaskStrategyService service = mock(MaskStrategyService.class);
        when(service.activeStrategies(7L)).thenReturn(List.of(strategy(7L, "BROKEN", "ALL", null, 1)));
        MaskStrategyResolver resolver = new MaskStrategyResolver(service);

        String out = resolver.resolve(7L, "audit.ip", "abcdefghijk", SensitiveType.NONE, Set.of());

        assertEquals(SensitiveMasker.genericMask("abcdefghijk"), out);
    }

    @Test
    void platformDefaultShouldApplyToBusinessTenant() {
        MaskStrategyService service = mock(MaskStrategyService.class);
        when(service.activeStrategies(0L)).thenReturn(List.of(strategy(0L, "MOBILE", "ALL", null, 1)));
        when(service.activeStrategies(7L)).thenReturn(List.of());
        MaskStrategyResolver resolver = new MaskStrategyResolver(service);

        String out = resolver.resolve(7L, "audit.ip", "13812341234", SensitiveType.NONE, Set.of());

        assertEquals("138****1234", out);
    }

    @Test
    void tenantStrategyShouldOverridePlatformOnEqualPriority() {
        MaskStrategyService service = mock(MaskStrategyService.class);
        when(service.activeStrategies(0L)).thenReturn(List.of(strategy(0L, "EMAIL", "ALL", null, 1)));
        when(service.activeStrategies(7L)).thenReturn(List.of(strategy(7L, "MOBILE", "ALL", null, 1)));
        MaskStrategyResolver resolver = new MaskStrategyResolver(service);

        String out = resolver.resolve(7L, "audit.ip", "13812341234", SensitiveType.NONE, Set.of());

        assertEquals("138****1234", out);
    }

    @Test
    void roleExemptionShouldStillReturnPlaintext() {
        MaskStrategyService service = mock(MaskStrategyService.class);
        when(service.activeStrategies(7L))
                .thenReturn(List.of(strategy(7L, "MOBILE", "ROLE_EXEMPT", "AUDITOR", 1)));
        MaskStrategyResolver resolver = new MaskStrategyResolver(service);

        String out = resolver.resolve(7L, "audit.ip", "13812341234", SensitiveType.NONE, Set.of("AUDITOR"));

        assertEquals("13812341234", out);
    }

    private static SysMaskStrategy strategy(long tenantId, String type, String scope,
                                            String exemptRoles, int priority) {
        SysMaskStrategy s = new SysMaskStrategy();
        s.setTenantId(tenantId);
        s.setFieldKey("audit.ip");
        s.setSensitiveType(type);
        s.setApplyScope(scope);
        s.setExemptRoles(exemptRoles);
        s.setEnabled(1);
        s.setPriority(priority);
        return s;
    }
}
