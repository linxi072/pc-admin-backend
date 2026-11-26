package com.acme.scaffold.common.tenant;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 租户解析单测：锁定“请求头覆盖必须先鉴权”，防止普通用户伪造 X-Tenant-Id 跨租户越权。 */
class TenantResolverTest {

    @Test
    void unauthorizedHeaderShouldNotOverrideClaim() {
        assertEquals(2L, TenantResolver.resolveTenantId("9", claims(2L), props(true), false));
    }

    @Test
    void authorizedHeaderShouldOverrideClaim() {
        assertEquals(9L, TenantResolver.resolveTenantId("9", claims(2L), props(true), true));
    }

    @Test
    void invalidAuthorizedHeaderShouldFallBackToClaim() {
        assertEquals(2L, TenantResolver.resolveTenantId("not-a-number", claims(2L), props(true), true));
        assertEquals(2L, TenantResolver.resolveTenantId("-1", claims(2L), props(true), true));
    }

    @Test
    void disabledTenantShouldIgnoreHeaderAndClaim() {
        assertEquals(0L, TenantResolver.resolveTenantId("9", claims(2L), props(false), true));
    }

    @Test
    void resolveFromClaimsShouldUseConfiguredClaimName() {
        assertEquals(7L, TenantResolver.resolveTenantIdFromClaims(claims(7L), props(true)));
    }

    private static Map<String, Object> claims(long tenantId) {
        return Map.of("tid", tenantId);
    }

    private static TenantProperties props(boolean enabled) {
        TenantProperties props = new TenantProperties();
        props.setEnabled(enabled);
        props.setDefaultTenantId(0L);
        props.setClaimName("tid");
        props.setPlatformAdminRoleCode("PLATFORM_ADMIN");
        return props;
    }
}
