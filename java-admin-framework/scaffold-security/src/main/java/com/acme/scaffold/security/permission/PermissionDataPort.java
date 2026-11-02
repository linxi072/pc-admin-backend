package com.acme.scaffold.security.permission;

import java.util.Set;

/**
 * 权限数据端口：解耦 {@code security.permission} 与 {@code system.entity} 的直连持久化访问。
 *
 * <p>B1 重构目标：原 {@link PermissionService} 直接 import 6 个 {@code system.entity} DO 并手写 jOOQ 查询，
 * 违反「security 只定义 Port、不直连其它特性模块实现」的模块边界。改为由本 Port 暴露纯数据查询契约，
 * 由 {@code system} 模块提供适配器实现（内部查询 sys_user_role / sys_role_api / sys_api_resource /
 * sys_role_menu / sys_menu / sys_role），{@link PermissionService} 仅依赖本接口。</p>
 */
public interface PermissionDataPort {

    /**
     * 取用户全部角色 id 并集；无角色返回空集。
     *
     * @param userId 用户 id
     * @return 角色 id 集合（去重）
     */
    Set<Long> roleIdsOfUser(Long userId);

    /**
     * 按角色 id 集合汇总接口权限码与菜单权限码（去重）。
     *
     * @param roleIds 角色 id 集合（空集合直接返回空）
     * @return 权限码集合（如 {@code system:user:read}）
     */
    Set<String> permissionCodesOfRoles(Set<Long> roleIds);

    /**
     * 按角色 id 集合汇总角色编码（去重）。
     *
     * @param roleIds 角色 id 集合（空集合直接返回空）
     * @return 角色编码集合（如 {@code ADMIN} / {@code USER}）
     */
    Set<String> roleCodesOfRoles(Set<Long> roleIds);
}
