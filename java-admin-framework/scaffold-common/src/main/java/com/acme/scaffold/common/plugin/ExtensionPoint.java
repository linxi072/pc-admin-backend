package com.acme.scaffold.common.plugin;

/**
 * 扩展点标记接口。所有「扩展点契约」应继承本接口（如 {@code MenuContributor}），
 * 外部或进程内实现经 {@link Extension} 注册后，由
 * {@link ExtensionRegistry#getExtensions(Class)} 按扩展点类型取回。
 */
public interface ExtensionPoint {
}
