package com.acme.scaffold.common.plugin;

import java.util.Objects;

/**
 * 已注册扩展的不可变描述。持有扩展 Bean 的元数据与实例引用。
 */
public record ExtensionDescriptor(
        String id,
        String name,
        String version,
        String description,
        int order,
        Class<?> type,
        Object instance) {

    public ExtensionDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(instance, "instance");
    }

    /** 该扩展是否实现了给定扩展点类型。 */
    public boolean supports(Class<?> extensionPoint) {
        return extensionPoint.isInterface() && extensionPoint.isAssignableFrom(type);
    }
}
