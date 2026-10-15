package com.acme.scaffold.config.datasource;

/**
 * 数据源路由上下文（ThreadLocal）。
 *
 * <p>在请求 / 方法作用域内标记当前应使用的数据源名称。置为 null 时，
 * {@link RoutingDataSource} 自动回落到主数据源（defaultTargetDataSource）。
 *
 * <p>切换方式有两种：
 * <ul>
 *   <li>编程式：{@code DataSourceContextHolder.set(name)} / {@code clear()}，
 *       或由 {@code DataSourceRouter.with(name, task)} 自动包裹还原；</li>
 *   <li>声明式：在方法上标注 {@code @WithDataSource("name")}（切面自动还原）。</li>
 * </ul>
 */
public final class DataSourceContextHolder {

    private static final ThreadLocal<String> CONTEXT = new ThreadLocal<>();

    private DataSourceContextHolder() {
    }

    public static void set(String dataSourceName) {
        CONTEXT.set(dataSourceName);
    }

    public static String get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
