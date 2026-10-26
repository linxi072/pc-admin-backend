package com.acme.scaffold.config.web;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * {@link TenantInterceptor} 请求级租户解析与上下文写入/清除测试。
 */
@ExtendWith(MockitoExtension.class)
class TenantInterceptorTest {

    @Mock
    private SecurityContextFacade securityContextFacade;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private TenantInterceptor interceptor(TenantProperties props) {
        return new TenantInterceptor(props, securityContextFacade);
    }

    @Test
    @DisplayName("请求头 X-Tenant-Id 优先解析租户")
    void resolvesFromHeaderWhenPresent() throws Exception {
        TenantProperties props = new TenantProperties();
        when(request.getHeader("X-Tenant-Id")).thenReturn("42");
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.empty());

        assertTrue(interceptor(props).preHandle(request, response, new Object()));
        assertEquals(42L, TenantContext.requireTenantId());
        assertFalse(TenantContext.isPlatformAdmin());
    }

    @Test
    @DisplayName("无请求头时回落到已认证主体的 tenantId")
    void resolvesFromPrincipalWhenNoHeader() throws Exception {
        TenantProperties props = new TenantProperties();
        CurrentPrincipal principal = new CurrentPrincipal(1L, "u", "n", 7L, "c", 1,
                Set.of("ROLE_USER"), Set.of());
        when(request.getHeader("X-Tenant-Id")).thenReturn(null);
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal));

        interceptor(props).preHandle(request, response, new Object());
        assertEquals(7L, TenantContext.requireTenantId());
    }

    @Test
    @DisplayName("主体含平台管理员角色码时标记平台管理员作用域")
    void marksPlatformAdmin() throws Exception {
        TenantProperties props = new TenantProperties(); // platformAdminRoleCode=PLATFORM_ADMIN
        CurrentPrincipal principal = new CurrentPrincipal(1L, "u", "n", 7L, "c", 1,
                Set.of("PLATFORM_ADMIN"), Set.of());
        when(request.getHeader("X-Tenant-Id")).thenReturn(null);
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.of(principal));

        interceptor(props).preHandle(request, response, new Object());
        assertEquals(7L, TenantContext.requireTenantId());
        assertTrue(TenantContext.isPlatformAdmin());
    }

    @Test
    @DisplayName("enabled=false 时忽略请求头，回落默认平台租户(0)")
    void disabledFallsBackToDefault() throws Exception {
        TenantProperties props = new TenantProperties();
        props.setEnabled(false);
        when(request.getHeader("X-Tenant-Id")).thenReturn("42");
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.empty());

        interceptor(props).preHandle(request, response, new Object());
        assertEquals(0L, TenantContext.requireTenantId());
        assertFalse(TenantContext.isPlatformAdmin());
    }

    @Test
    @DisplayName("afterCompletion 清除上下文，避免线程池复用串台")
    void clearsContextAfterCompletion() throws Exception {
        TenantProperties props = new TenantProperties();
        when(request.getHeader("X-Tenant-Id")).thenReturn("42");
        when(securityContextFacade.getCurrentPrincipal()).thenReturn(Optional.empty());

        TenantInterceptor it = interceptor(props);
        it.preHandle(request, response, new Object());
        assertTrue(TenantContext.isSet());
        it.afterCompletion(request, response, new Object(), null);
        assertFalse(TenantContext.isSet());
    }
}
