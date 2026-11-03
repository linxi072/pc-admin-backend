package com.acme.scaffold.admin.plugin;

import com.acme.scaffold.common.plugin.ExtensionRegistry;
import com.acme.scaffold.plugin.PluginManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 插件装配（默认关闭，需 {@code scaffold.plugin.enabled=true} 才激活加载）。
 *
 * <p>启动后扫描 {@code scaffold.plugin.dir} 下的 {@code *.jar}，经 {@link PluginManager}
 * 加载并注入扩展点实现（菜单/路由/权限码）。默认关闭以确保既有行为不变、且普通部署无需插件目录。
 */
@Configuration
public class PluginAutoConfiguration {

    @Bean
    public PluginManager pluginManager(ApplicationContext applicationContext, ExtensionRegistry registry) {
        return new PluginManager(applicationContext, registry);
    }

    @Bean
    public PluginLoaderRunner pluginLoaderRunner(PluginManager pluginManager,
                                                 @Value("${scaffold.plugin.enabled:false}") boolean enabled,
                                                 @Value("${scaffold.plugin.dir:plugins}") String dir) {
        return new PluginLoaderRunner(pluginManager, enabled, dir);
    }

    static class PluginLoaderRunner implements ApplicationRunner {

        private static final Logger log = LoggerFactory.getLogger(PluginLoaderRunner.class);

        private final PluginManager pluginManager;
        private final boolean enabled;
        private final String dir;

        PluginLoaderRunner(PluginManager pluginManager, boolean enabled, String dir) {
            this.pluginManager = pluginManager;
            this.enabled = enabled;
            this.dir = dir;
        }

        @Override
        public void run(ApplicationArguments args) {
            if (!enabled) {
                log.debug("插件加载未启用（scaffold.plugin.enabled=false），跳过");
                return;
            }
            Path dirPath = Path.of(dir);
            if (!Files.isDirectory(dirPath)) {
                log.info("插件目录不存在，跳过加载：{}", dirPath);
                return;
            }
            try (var stream = Files.list(dirPath)) {
                stream.filter(p -> p.toString().endsWith(".jar"))
                        .forEach(jar -> {
                            try {
                                pluginManager.loadPlugin(jar);
                            } catch (IOException ex) {
                                log.error("加载插件失败，已跳过：{}", jar, ex);
                            }
                        });
            } catch (IOException ex) {
                log.error("扫描插件目录失败：{}", dirPath, ex);
            }
        }
    }
}
