package com.acme.scaffold.common.plugin;

import java.util.List;

/**
 * 扩展注册表：收集并暴露所有已注册的 {@link Extension} 实现。
 *
 * <p>进程内（组件扫描路径下）的 {@code @Extension} Bean 在启动期由
 * {@code DefaultExtensionRegistry} 自动收集；运行时由 {@code PluginManager} 经
 * {@link #register(ExtensionDescriptor)} 追加外部 jar 加载的扩展。
 */
public interface ExtensionRegistry {

    /** 按扩展点类型取回所有实现（已按 order 升序）。 */
    <T> List<T> getExtensions(Class<T> extensionPoint);

    /** 全部已注册扩展描述。 */
    List<ExtensionDescriptor> all();

    /** 按 id 取回扩展描述（含实例）。 */
    ExtensionDescriptor getDescriptor(String id);

    /** 运行时注册一个扩展（供 PluginManager 加载外部 jar 后调用）。 */
    void register(ExtensionDescriptor descriptor);

    /** 重新扫描进程内所有 @Extension Bean（一般无需手动调用）。 */
    void refresh();
}
