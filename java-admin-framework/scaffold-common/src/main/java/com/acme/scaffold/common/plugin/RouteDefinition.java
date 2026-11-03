package com.acme.scaffold.common.plugin;

/**
 * 外部扩展贡献的前端路由定义（数据契约，不含持久化）。
 */
public record RouteDefinition(
        String path,
        String name,
        String component,
        String title,
        String icon,
        String permission,
        String parentPath) {
}
