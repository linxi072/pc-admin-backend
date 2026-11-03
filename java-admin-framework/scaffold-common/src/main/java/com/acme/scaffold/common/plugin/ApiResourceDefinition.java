package com.acme.scaffold.common.plugin;

/**
 * 外部扩展贡献的 API 资源（权限码）定义（数据契约，不含持久化）。
 * 由 {@code ApiResourceAutoSyncRunner} 风格的装配逻辑消费，自动登记权限码。
 */
public record ApiResourceDefinition(
        String code,
        String name,
        String path,
        String method,
        String permission) {
}
