package com.acme.scaffold.security.permission;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 权限聚合：根据用户角色汇总接口权限码与角色码，供 JWT 签发与接口鉴权使用。
 *
 * <p>B1 重构：原实现直连 {@code system.entity} 手写 jOOQ 查询，违反「security 只定义 Port、
 * 不直连其它特性模块实现」的模块边界。现委托 {@link PermissionDataPort}（由 system 模块适配器实现），
 * 本类置于 {@code security.permission} 包，不再直接 import 任何系统域持久化对象。</p>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionDataPort permissionDataPort;

    public Set<String> getPermissions(Long userId) {
        Set<Long> roleIds = permissionDataPort.roleIdsOfUser(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return permissionDataPort.permissionCodesOfRoles(roleIds);
    }

    public Set<String> getRoleCodes(Long userId) {
        Set<Long> roleIds = permissionDataPort.roleIdsOfUser(userId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return permissionDataPort.roleCodesOfRoles(roleIds);
    }
}
