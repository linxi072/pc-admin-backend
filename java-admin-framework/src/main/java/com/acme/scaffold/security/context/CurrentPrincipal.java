package com.acme.scaffold.security.context;

import java.util.Set;

/**
 * 当前登录主体。仅携带非敏感元数据，密码/原始 Token 绝不进入该对象。
 */
public record CurrentPrincipal(
        Long userId,
        String username,
        String displayName,
        Long tenantId,
        String clientId,
        Integer tokenVersion,
        Set<String> roles,
        Set<String> permissions) {

    public boolean hasPermission(String permissionCode) {
        return permissions != null && permissions.contains(permissionCode);
    }
}
