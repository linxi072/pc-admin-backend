package com.acme.scaffold.plugin;

import com.acme.scaffold.common.plugin.ExtensionDescriptor;
import com.acme.scaffold.common.plugin.ExtensionPoint;
import com.acme.scaffold.common.plugin.ExtensionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 插件生命周期管理器：加载外部 jar，按清单实例化 {@link ExtensionPoint} 实现，
 * 经 {@link AutowireCapableBeanFactory#createBean(Class)} 完成依赖注入后注册到
 * {@link ExtensionRegistry}。
 *
 * <p>每个插件拥有独立的 {@link URLClassLoader}（父加载器为当前上下文类加载器，
 * 因此扩展类可访问 common 中的扩展点契约）。加载失败时关闭其类加载器并向上抛出，
 * 已部分注册的扩展不会泄漏到注册表。卸载主要释放类加载器资源。
 */
public class PluginManager {

    private static final Logger log = LoggerFactory.getLogger(PluginManager.class);

    private final ApplicationContext applicationContext;
    private final ExtensionRegistry registry;
    private final Map<String, PluginHandle> loaded = new ConcurrentHashMap<>();

    public PluginManager(ApplicationContext applicationContext, ExtensionRegistry registry) {
        this.applicationContext = applicationContext;
        this.registry = registry;
    }

    /**
     * 加载一个插件 jar 并注册其中声明的扩展。
     *
     * @param jar 插件 jar 路径（须为常规文件，内含 META-INF/scaffold-extensions/extensions.json）
     * @throws IOException 读取清单、实例化扩展或加载失败
     */
    public synchronized void loadPlugin(Path jar) throws IOException {
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("插件路径不是常规文件: " + jar);
        }
        URL url = jar.toUri().toURL();
        URLClassLoader cl = new URLClassLoader(new URL[]{url}, Thread.currentThread().getContextClassLoader());
        String pluginId;
        try {
            ExtensionScanner.PluginManifest manifest = ExtensionScanner.scan(cl);
            pluginId = manifest.id != null ? manifest.id : jar.getFileName().toString();
            if (loaded.containsKey(pluginId)) {
                throw new IllegalStateException("插件已加载: " + pluginId);
            }
            AutowireCapableBeanFactory autowire = applicationContext.getAutowireCapableBeanFactory();
            List<ExtensionDescriptor> descriptors = new ArrayList<>();
            for (ExtensionScanner.ExtensionEntry e : manifest.extensions) {
                if (e.className == null || e.className.isBlank()) {
                    continue;
                }
                Class<?> clazz = Class.forName(e.className, true, cl);
                if (!ExtensionPoint.class.isAssignableFrom(clazz)) {
                    throw new IllegalArgumentException("扩展类必须实现 ExtensionPoint: " + e.className);
                }
                Object instance = autowire.createBean(clazz);
                String id = pluginId + ":" + (e.name != null && !e.name.isBlank() ? e.name : clazz.getName());
                int order = e.order != null ? e.order : 0;
                descriptors.add(new ExtensionDescriptor(id,
                        e.name != null ? e.name : clazz.getSimpleName(),
                        e.version != null ? e.version : "1.0.0",
                        e.description != null ? e.description : "",
                        order, clazz, instance));
            }
            for (ExtensionDescriptor d : descriptors) {
                registry.register(d);
            }
            loaded.put(pluginId, new PluginHandle(pluginId, jar, cl, descriptors));
            log.info("插件 {} 加载完成，注入扩展 {} 个", pluginId, descriptors.size());
        } catch (RuntimeException | ClassNotFoundException ex) {
            try {
                cl.close();
            } catch (IOException ignore) {
                // 忽略关闭异常，原异常优先
            }
            throw new IOException("插件加载失败: " + jar, ex);
        }
    }

    /**
     * 卸载插件（释放其类加载器资源）。
     * 注：已注册到 {@link ExtensionRegistry} 的扩展实例为追加式，卸载不自动撤销；
     * 如需完整卸载需扩展 registry 支持按插件 id 撤销。
     */
    public synchronized void unloadPlugin(String pluginId) throws IOException {
        PluginHandle handle = loaded.remove(pluginId);
        if (handle == null) {
            return;
        }
        try {
            handle.classLoader().close();
        } catch (IOException ex) {
            log.warn("关闭插件类加载器失败: {}", pluginId, ex);
        }
    }

    public List<String> loadedPluginIds() {
        return new ArrayList<>(loaded.keySet());
    }

    private record PluginHandle(String id, Path jar, URLClassLoader classLoader,
                                List<ExtensionDescriptor> descriptors) {
    }
}
