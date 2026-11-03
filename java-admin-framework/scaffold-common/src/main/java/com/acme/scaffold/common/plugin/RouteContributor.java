package com.acme.scaffold.common.plugin;

import java.util.List;

/**
 * 路由贡献扩展点：返回该插件要注入的前端路由。
 */
public interface RouteContributor extends ExtensionPoint {
    List<RouteDefinition> contributeRoutes();
}
