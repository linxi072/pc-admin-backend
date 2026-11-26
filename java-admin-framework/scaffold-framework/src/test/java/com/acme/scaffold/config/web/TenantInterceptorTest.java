package com.acme.scaffold.config.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** 租户拦截器单测：普通用户的 X-Tenant-Id 伪造头必须被忽略，仅平台管理员可跨租户覆盖。 */
class TenantInterceptorTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void normalUserHeaderShouldBeIgnored() {
        TenantInterceptor interceptor = interceptor(Optional.of(principal(2L, Set.of("USER"))));

        interceptor.preHandle(request("9"), mock(HttpServletResponse.class), new Object());

        assertEquals(2L, TenantContext.requireTenantId());
        assertFalse(TenantContext.isPlatformAdmin());
    }

    @Test
    void platformAdminHeaderShouldOverrideClaim() {
        TenantInterceptor interceptor = interceptor(Optional.of(principal(2L, Set.of("PLATFORM_ADMIN"))));

        interceptor.preHandle(request("9"), mock(HttpServletResponse.class), new Object());

        assertEquals(9L, TenantContext.requireTenantId());
        assertTrue(TenantContext.isPlatformAdmin());
    }

    @Test
    void anonymousHeaderShouldFallBackToDefaultTenant() {
        TenantInterceptor interceptor = interceptor(Optional.empty());

        interceptor.preHandle(request("9"), mock(HttpServletResponse.class), new Object());

        assertEquals(0L, TenantContext.requireTenantId());
        assertFalse(TenantContext.isPlatformAdmin());
    }

    private static TenantInterceptor interceptor(Optional<CurrentPrincipal> principal) {
        SecurityContextFacade facade = mock(SecurityContextFacade.class);
        when(facade.getCurrentPrincipal()).thenReturn(principal);
        return new TenantInterceptor(props(), facade);
    }

    private static HttpServletRequest request(String tenantHeader) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Tenant-Id")).thenReturn(tenantHeader);
        return request;
    }

    private static CurrentPrincipal principal(long tenantId, Set<String> roles) {
        return new CurrentPrincipal(1L, "alice", "Alice", tenantId, "web", 1, roles, Set.of());
    }

    private static TenantProperties props() {
        TenantProperties props = new TenantProperties();
        props.setEnabled(true);
        props.setDefaultTenantId(0L);
        props.setHeaderName("X-Tenant-Id");
        props.setClaimName("tid");
        props.setPlatformAdminRoleCode("PLATFORM_ADMIN");
        return props;
    }
}
