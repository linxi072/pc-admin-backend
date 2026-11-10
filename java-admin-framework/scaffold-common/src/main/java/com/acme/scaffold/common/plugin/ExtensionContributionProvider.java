package com.acme.scaffold.common.plugin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 扩展贡献聚合器：从 {@link ExtensionRegistry} 收集各扩展点贡献的数据（菜单/路由/权限码），
 * 供业务模块（system 菜单装配、前端路由下发、API 资源自动登记）消费，无需改 scaffold 源码。
 *
 * <p>单个扩展贡献抛异常时仅记录日志并跳过，不阻断其余扩展与宿主启动（错误隔离）。
 */
@Slf4j
@Component
public class ExtensionContributionProvider {

    private final ExtensionRegistry registry;

    public ExtensionContributionProvider(ExtensionRegistry registry) {
        this.registry = registry;
    }

    public List<MenuDefinition> collectMenus() {
        List<MenuDefinition> all = new ArrayList<>();
        for (MenuContributor c : registry.getExtensions(MenuContributor.class)) {
            try {
                all.addAll(c.contributeMenus());
            } catch (RuntimeException ex) {
                log.warn("扩展 {} 贡献菜单失败，已跳过", c.getClass().getName(), ex);
            }
        }
        all.sort(Comparator.comparingInt(MenuDefinition::order));
        return all;
    }

    public List<RouteDefinition> collectRoutes() {
        List<RouteDefinition> all = new ArrayList<>();
        for (RouteContributor c : registry.getExtensions(RouteContributor.class)) {
            try {
                all.addAll(c.contributeRoutes());
            } catch (RuntimeException ex) {
                log.warn("扩展 {} 贡献路由失败，已跳过", c.getClass().getName(), ex);
            }
        }
        return all;
    }

    public List<ApiResourceDefinition> collectApiResources() {
        List<ApiResourceDefinition> all = new ArrayList<>();
        for (ApiResourceContributor c : registry.getExtensions(ApiResourceContributor.class)) {
            try {
                all.addAll(c.contributeApiResources());
            } catch (RuntimeException ex) {
                log.warn("扩展 {} 贡献 API 资源失败，已跳过", c.getClass().getName(), ex);
            }
        }
        return all;
    }
}
