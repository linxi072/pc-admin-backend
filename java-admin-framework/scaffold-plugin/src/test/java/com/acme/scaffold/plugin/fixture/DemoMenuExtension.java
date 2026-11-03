package com.acme.scaffold.plugin.fixture;

import com.acme.scaffold.common.plugin.Extension;
import com.acme.scaffold.common.plugin.MenuContributor;
import com.acme.scaffold.common.plugin.MenuDefinition;

import java.util.List;

/**
 * 测试用插件贡献：打包进外部 jar 后由 {@code PluginManager} 加载，
 * 演示「独立 jar 注入后台菜单」的端到端链路。
 */
@Extension(name = "demo-menu", description = "demo menu contributor")
public class DemoMenuExtension implements MenuContributor {

    @Override
    public List<MenuDefinition> contributeMenus() {
        return List.of(new MenuDefinition(
                "plugin-demo", "system", "演示插件菜单", "/plugin/demo", "el-icon-menu", "plugin:demo:view", 100));
    }
}
