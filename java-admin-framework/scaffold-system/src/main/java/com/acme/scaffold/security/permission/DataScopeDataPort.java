package com.acme.scaffold.security.permission;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 数据权限数据端口：解耦 {@code security.permission} 与 {@code system.entity} 的直连持久化访问。
 *
 * <p>B5 重构目标：原 {@link DefaultDataScopeProvider} 直接 import 5 个 {@code system.entity} DO
 * （sys_user_role / sys_role_data_scope / sys_user_org / sys_org / sys_role_data_scope_org）并手写 jOOQ 查询，
 * 违反「security 只定义 Port、不直连其它特性模块实现」的模块边界。改为由本 Port 暴露纯数据查询契约，
 * 由 {@code system} 模块提供适配器实现，{@link DefaultDataScopeProvider} 仅依赖本接口与返回的领域抽象类型，
 * 不再接触任何 {@code system.entity} 持久化对象。</p>
 */
public interface DataScopeDataPort {

    /**
     * 取用户全部角色 id 并集；无角色返回空集。
     *
     * @param userId 用户 id
     * @return 角色 id 集合（去重）
     */
    Set<Long> roleIdsOfUser(Long userId);

    /**
     * 按角色 id 集合与资源码汇总数据范围规则（ruleId -> scopeType）。
     * 调用方据此裁决 ALL / SELF / 机构类规则；无规则返回空集。
     *
     * @param roleIds       角色 id 集合（空集合直接返回空）
     * @param resourceCode  资源编码，例如 {@code system:user}
     * @return 规则 id 到 scopeType 的映射
     */
    Map<Long, String> dataScopesOfRoles(Set<Long> roleIds, String resourceCode);

    /**
     * 用户主部门 id（sys_user_org 中 is_primary=1）；未绑定返回 null。
     *
     * @param userId 用户 id
     * @return 主部门 id，可能为 null
     */
    Long primaryOrgIdOfUser(Long userId);

    /**
     * 某机构的全部下级机构 id 集合（基于 sys_org.ancestors 物化路径匹配）；无下级返回空集。
     *
     * @param orgId 机构 id
     * @return 下级机构 id 集合
     */
    Set<Long> childOrgIds(Long orgId);

    /**
     * 某 CUSTOM 数据范围规则配置的机构 id 集合（sys_role_data_scope_org）；无返回空集。
     *
     * @param ruleId 数据范围规则 id
     * @return 机构 id 集合
     */
    Set<Long> customOrgIds(Long ruleId);
}
