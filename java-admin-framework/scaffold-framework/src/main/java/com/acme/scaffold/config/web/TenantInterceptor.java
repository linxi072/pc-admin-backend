package com.acme.scaffold.config.web;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.common.tenant.TenantProperties;
import com.acme.scaffold.common.tenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 租户上下文拦截器（M19 P3）：在每个 Web 请求开始时解析并写入 {@link TenantContext}，
 * 请求结束（含异常）时清除，避免线程池复用导致作用域串台。
 *
 * <p>解析优先级复用 {@link TenantResolver}：请求头 X-Tenant-Id &gt; 已认证主体携带的 tid &gt;
 * 默认租户。{@code enabled=false} 时回落默认租户（隔离关闭，全部视为平台租户）。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class TenantInterceptor implements HandlerInterceptor {

    private final TenantProperties tenantProperties;
    private final SecurityContextFacade securityContextFacade;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String headerName = tenantProperties.getHeaderName();
        String headerValue = (headerName != null) ? request.getHeader(headerName) : null;

        // 已认证主体的 tenantId 视为 Token Claim 视角（优先级次之）
        Map<String, Object> claims = null;
        Optional<CurrentPrincipal> principal = securityContextFacade.getCurrentPrincipal();
        if (principal.isPresent() && principal.get().tenantId() != null) {
            claims = new HashMap<>(1);
            claims.put(tenantProperties.getClaimName(), principal.get().tenantId());
        }

        long tenantId = TenantResolver.resolveTenantId(headerValue, claims, tenantProperties);
        boolean platformAdmin = principal
                .map(p -> TenantResolver.isPlatformAdmin(p.roles(), tenantProperties))
                .orElse(false);

        TenantContext.set(tenantId, platformAdmin);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                               Object handler, Exception ex) {
        TenantContext.clear();
    }
}
