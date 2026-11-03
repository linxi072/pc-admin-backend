package com.acme.scaffold.config.multiregion;

import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.config.datasource.DataSourceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 多区域租户路由拦截器（R4，Web 层）。
 *
 * <p>在 {@code TenantInterceptor} 已写入 {@link TenantContext} 之后执行：读取当前租户，
 * 经 {@link TenantUnitMapping} 解析其部署单元（数据源名），并写入 {@link DataSourceContextHolder}，
 * 使后续通过主 DSLContext（构建在 RoutingDataSource 之上）的 jOOQ 读写自动落到该单元的物理库。
 * 请求结束（含异常）时清除路由上下文，避免线程池复用串台。</p>
 *
 * <p>仅当多区域启用时该拦截器 Bean 才存在（由 {@link MultiRegionAutoConfiguration} 条件化装配），
 * 未启用时 WebConfig 不会注册它，路由上下文恒为 null → RoutingDataSource 回落主库，零回归。</p>
 */
@RequiredArgsConstructor
public class MultiRegionInterceptor implements HandlerInterceptor {

    private final TenantUnitMapping mapping;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (TenantContext.isSet()) {
            long tenantId = TenantContext.requireTenantId();
            DataSourceContextHolder.set(mapping.resolve(tenantId));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        DataSourceContextHolder.clear();
    }
}
