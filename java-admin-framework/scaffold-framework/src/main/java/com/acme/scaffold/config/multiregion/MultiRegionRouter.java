package com.acme.scaffold.config.multiregion;

import com.acme.scaffold.common.tenant.TenantContext;
import com.acme.scaffold.config.datasource.DataSourceContextHolder;

import java.util.concurrent.Callable;

/**
 * 编程式多区域路由助手（R4，非 Web 路径）。
 *
 * <p>供定时任务 / 事件处理器 / 跨租户运维脚本等「无 Web 请求上下文」场景使用：
 * 在指定租户作用域内执行任务，并自动按 {@link TenantUnitMapping} 切换数据源路由上下文，
 * 结束后还原调用前状态（含租户上下文与数据源路由上下文），支持嵌套。</p>
 *
 * <p>示例：
 * <pre>
 *   multiRegionRouter.runWithTenant(tenantId, () -&gt; reportDsl.selectFrom(...).fetch());
 * </pre>
 */
public class MultiRegionRouter {

    private final TenantUnitMapping mapping;

    public MultiRegionRouter(TenantUnitMapping mapping) {
        this.mapping = mapping;
    }

    /** 在指定租户 + 其部署单元数据源上下文中执行无返回值任务，自动还原。 */
    public void runWithTenant(long tenantId, Runnable task) {
        String prevDs = DataSourceContextHolder.get();
        TenantContext.TenantScope prevTenant = TenantContext.current();
        DataSourceContextHolder.set(mapping.resolve(tenantId));
        TenantContext.setTenantId(tenantId);
        try {
            task.run();
        } finally {
            restoreDs(prevDs);
            restoreTenant(prevTenant);
        }
    }

    /** 在指定租户 + 其部署单元数据源上下文中执行有返回值任务，自动还原。 */
    public <T> T callWithTenant(long tenantId, Callable<T> task) throws Exception {
        String prevDs = DataSourceContextHolder.get();
        TenantContext.TenantScope prevTenant = TenantContext.current();
        DataSourceContextHolder.set(mapping.resolve(tenantId));
        TenantContext.setTenantId(tenantId);
        try {
            return task.call();
        } finally {
            restoreDs(prevDs);
            restoreTenant(prevTenant);
        }
    }

    private void restoreDs(String prev) {
        if (prev == null) {
            DataSourceContextHolder.clear();
        } else {
            DataSourceContextHolder.set(prev);
        }
    }

    private void restoreTenant(TenantContext.TenantScope prev) {
        if (prev == null) {
            TenantContext.clear();
        } else {
            TenantContext.set(prev.tenantId(), prev.platformAdmin());
        }
    }
}
