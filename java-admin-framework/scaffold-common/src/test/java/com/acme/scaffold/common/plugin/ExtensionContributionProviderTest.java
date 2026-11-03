package com.acme.scaffold.common.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ExtensionContributionProviderTest {

    private static ExtensionDescriptor desc(String id, MenuContributor c) {
        return new ExtensionDescriptor(id, id, "1.0", "", 0, c.getClass(), c);
    }

    @Test
    void collectMenusMergesFromAllContributorsAndSortsByMenuOrder() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registry.register(desc("m1", () -> List.of(new MenuDefinition("x", "", "X", "/x", null, null, 20))));
        registry.register(desc("m2", () -> List.of(
                new MenuDefinition("y", "", "Y", "/y", null, null, 5),
                new MenuDefinition("z", "", "Z", "/z", null, null, 15))));

        ExtensionContributionProvider provider = new ExtensionContributionProvider(registry);
        List<MenuDefinition> menus = provider.collectMenus();

        assertThat(menus).extracting(MenuDefinition::code).containsExactly("y", "z", "x");
    }

    @Test
    void collectMenusAndRoutesAndApiResourcesAggregateSeparately() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registry.register(desc("m", new MenuContributor() {
            @Override
            public List<MenuDefinition> contributeMenus() {
                return List.of(new MenuDefinition("m1", "", "M1", "/m1", null, null, 1));
            }
        }));
        registry.register(new ExtensionDescriptor("r", "r", "1.0", "", 0, RouteFixture.class, new RouteFixture()));
        registry.register(new ExtensionDescriptor("a", "a", "1.0", "", 0, ApiFixture.class, new ApiFixture()));

        ExtensionContributionProvider provider = new ExtensionContributionProvider(registry);
        assertThat(provider.collectMenus()).extracting(MenuDefinition::code).containsExactly("m1");
        assertThat(provider.collectRoutes()).extracting(RouteDefinition::path).containsExactly("/r1");
        assertThat(provider.collectApiResources()).extracting(ApiResourceDefinition::code).containsExactly("api1");
    }

    @Test
    void faultyContributorIsIsolated() {
        DefaultExtensionRegistry registry = new DefaultExtensionRegistry();
        registry.register(desc("bad", () -> {
            throw new RuntimeException("boom");
        }));
        registry.register(desc("good", () -> List.of(new MenuDefinition("ok", "", "OK", "/ok", null, null, 1))));

        ExtensionContributionProvider provider = new ExtensionContributionProvider(registry);
        assertThat(provider.collectMenus()).extracting(MenuDefinition::code).containsExactly("ok");
    }

    static class RouteFixture implements RouteContributor {
        @Override
        public List<RouteDefinition> contributeRoutes() {
            return List.of(new RouteDefinition("/r1", "r1", "R1.vue", "R1", null, null, ""));
        }
    }

    static class ApiFixture implements ApiResourceContributor {
        @Override
        public List<ApiResourceDefinition> contributeApiResources() {
            return List.of(new ApiResourceDefinition("api1", "API1", "/api/1", "GET", "api1:view"));
        }
    }
}
