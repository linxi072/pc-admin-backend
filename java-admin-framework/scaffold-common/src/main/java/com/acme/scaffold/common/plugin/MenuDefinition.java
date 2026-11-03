package com.acme.scaffold.common.plugin;

/**
 * 外部扩展贡献的菜单节点定义（数据契约，不含持久化）。
 * 由各业务模块的菜单装配逻辑消费（如 R5 CMS 落地时写入菜单表）。
 */
public record MenuDefinition(
        String code,
        String parentCode,
        String title,
        String path,
        String icon,
        String permission,
        int order) {
}
