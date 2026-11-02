package com.acme.scaffold.security.permission;

/**
 * 数据权限资源视图：标注了 {@code @DataScope} 且接入了数据过滤的资源。
 *
 * <p>本类型由 {@code DataScopeResourceRegistry}（同包）在应用启动时自动扫描得到，
 * 是安全模块对外暴露的契约（端口）；{@code system} 模块的配置接口消费它，
 * 但安全模块不反向依赖 {@code system}，从而维持「低层不依赖高层」的包依赖方向。
 *
 * <p>配置页据此动态渲染可配置的资源清单，无需在前端硬编码资源编码。
 *
 * @param resourceCode 资源编码，例如 {@code system:user}
 * @param resourceName 资源展示名（取自 {@code @DataScope(name=...)}，缺省回退为编码）
 */
public record DataScopeResourceView(String resourceCode, String resourceName) {
}
