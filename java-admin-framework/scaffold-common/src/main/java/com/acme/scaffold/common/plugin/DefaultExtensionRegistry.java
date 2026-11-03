package com.acme.scaffold.common.plugin;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认扩展注册表：
 * <ul>
 *   <li>在 {@link SmartInitializingSingleton#afterSingletonsInstantiated()} 中扫描全部
 *       {@code @Extension} Bean（进程内组件扫描路径下），转为 {@link ExtensionDescriptor} 收集；</li>
 *   <li>支持运行时经 {@link #register(ExtensionDescriptor)} 追加外部 jar 加载的扩展；</li>
 *   <li>按扩展点类型查询时再做类型过滤与 order 排序，避免维护多份索引。</li>
 * </ul>
 */
@Component
public class DefaultExtensionRegistry implements ExtensionRegistry, ApplicationContextAware, SmartInitializingSingleton {

    private final Map<String, ExtensionDescriptor> byId = new ConcurrentHashMap<>();
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void afterSingletonsInstantiated() {
        refresh();
    }

    @Override
    public void refresh() {
        if (applicationContext == null) {
            return;
        }
        // 进程内 Bean 只收集一次；运行时 register 的扩展以 id 去重，不被覆盖。
        applicationContext.getBeansWithAnnotation(Extension.class).forEach((beanName, bean) -> {
            if (byId.containsKey(beanName)) {
                return;
            }
            Extension meta = AnnotationUtils.findAnnotation(bean.getClass(), Extension.class);
            String name = meta != null && !meta.name().isBlank() ? meta.name() : beanName;
            String version = meta != null ? meta.version() : "1.0.0";
            String description = meta != null ? meta.description() : "";
            int order = meta != null ? meta.order() : 0;
            byId.put(beanName, new ExtensionDescriptor(beanName, name, version, description, order, bean.getClass(), bean));
        });
    }

    @Override
    public void register(ExtensionDescriptor descriptor) {
        byId.put(descriptor.id(), descriptor);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> List<T> getExtensions(Class<T> extensionPoint) {
        if (!extensionPoint.isInterface()) {
            return List.of();
        }
        return byId.values().stream()
                .filter(d -> extensionPoint.isAssignableFrom(d.type()))
                .sorted(Comparator.comparingInt(ExtensionDescriptor::order))
                .map(d -> extensionPoint.cast(d.instance()))
                .toList();
    }

    @Override
    public List<ExtensionDescriptor> all() {
        return List.copyOf(byId.values());
    }

    @Override
    public ExtensionDescriptor getDescriptor(String id) {
        return byId.get(id);
    }
}
