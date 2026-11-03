package com.acme.scaffold.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.scaffold.common.plugin.DefaultExtensionRegistry;
import com.acme.scaffold.common.plugin.ExtensionRegistry;
import com.acme.scaffold.common.plugin.MenuContributor;
import com.acme.scaffold.common.plugin.MenuDefinition;
import com.acme.scaffold.plugin.fixture.DemoMenuExtension;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

class PluginManagerTest {

    private static final String MANIFEST_JSON = """
            {
              "id": "demo-plugin",
              "name": "Demo Plugin",
              "version": "1.0.0",
              "contributor": "acme",
              "extensions": [
                {
                  "name": "demo-menu",
                  "className": "com.acme.scaffold.plugin.fixture.DemoMenuExtension",
                  "version": "1.0.0",
                  "description": "demo",
                  "order": 0
                }
              ]
            }
            """;

    private Path buildPluginJar() throws Exception {
        Path jar = Files.createTempFile("demo-plugin", ".jar");
        URL classUrl = DemoMenuExtension.class.getProtectionDomain().getCodeSource().getLocation();
        Path classesDir = Path.of(classUrl.toURI());
        Path classFile = classesDir.resolve("com/acme/scaffold/plugin/fixture/DemoMenuExtension.class");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar));
             InputStream in = Files.newInputStream(classFile)) {
            jos.putNextEntry(new JarEntry("com/acme/scaffold/plugin/fixture/DemoMenuExtension.class"));
            jos.write(in.readAllBytes());
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("META-INF/scaffold-extensions/extensions.json"));
            jos.write(MANIFEST_JSON.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return jar;
    }

    @Test
    void loadPluginRegistersExtensionAndContributionVisible() throws Exception {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(DefaultExtensionRegistry.class);
        ctx.refresh();
        ExtensionRegistry registry = ctx.getBean(ExtensionRegistry.class);

        PluginManager manager = new PluginManager(ctx, registry);
        Path jar = buildPluginJar();
        manager.loadPlugin(jar);

        List<MenuContributor> contributors = registry.getExtensions(MenuContributor.class);
        assertThat(contributors).hasSize(1);
        MenuDefinition menu = contributors.get(0).contributeMenus().get(0);
        assertThat(menu.code()).isEqualTo("plugin-demo");
        assertThat(menu.path()).isEqualTo("/plugin/demo");

        assertThat(manager.loadedPluginIds()).contains("demo-plugin");
        manager.unloadPlugin("demo-plugin");
        ctx.close();
    }

    @Test
    void loadPluginRejectsNonRegularFile() {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.register(DefaultExtensionRegistry.class);
        ctx.refresh();
        PluginManager manager = new PluginManager(ctx, ctx.getBean(ExtensionRegistry.class));
        assertThatThrownBy(() -> manager.loadPlugin(Files.createTempDirectory("not-a-jar")))
                .isInstanceOf(IllegalArgumentException.class);
        ctx.close();
    }
}
