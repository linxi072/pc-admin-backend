package com.acme.scaffold.common.plugin;

import java.util.List;

/**
 * 菜单贡献扩展点：返回该插件要注入的后台菜单节点。
 */
public interface MenuContributor extends ExtensionPoint {
    List<MenuDefinition> contributeMenus();
}
