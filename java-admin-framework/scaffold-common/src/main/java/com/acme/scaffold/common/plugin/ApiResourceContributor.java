package com.acme.scaffold.common.plugin;

import java.util.List;

/**
 * API 资源（权限码）贡献扩展点：返回该插件要登记的端点权限码。
 */
public interface ApiResourceContributor extends ExtensionPoint {
    List<ApiResourceDefinition> contributeApiResources();
}
