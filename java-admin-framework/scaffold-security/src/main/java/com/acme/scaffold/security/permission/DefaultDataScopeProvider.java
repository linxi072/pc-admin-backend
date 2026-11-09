package com.acme.scaffold.security.permission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 默认数据权限解析：基于角色数据范围规则（sys_role_data_scope / sys_role_data_scope_org）计算可见范围。
 * 持久层由 B5 抽离至 {@link DataScopeDataPort}，本类只保留跨角色合并裁决等纯业务逻辑，
 * 不再直连 {@code system.entity}（模块边界：security 只定义 Port，由 system 提供适配器实现）。
 *
 * <p>规则合并约定（纯 N:N：用户可能绑定多个角色，各角色可配置独立的数据范围规则，需跨角色合并）：
 * <ul>
 *   <li>任一规则为 ALL => 整体 ALL（最高优先级）；</li>
 *   <li>否则任一规则为 SELF => 本人（优先于机构类规则）；</li>
 *   <li>其余机构类规则（DEPT / DEPT_AND_CHILD / CUSTOM）取并集。</li>
 * </ul>
 * 先收集全部角色的规则再统一裁决，避免某角色的 SELF/ALL 提前 return 短路而漏掉另一角色的相反语义
 * （该缺陷在纯 N:N 多角色后才会暴露，单角色时代遍历顺序不影响结果）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDataScopeProvider implements DataScopeProvider {

    private final DataScopeDataPort port;
    private final DataScopeProperties properties;

    @Override
    public DataScopeResult resolve(Long userId, String resourceCode) {
        if (userId == null) {
            log.warn("数据权限解析 userId 为空，按默认兜底策略 {} 处理（fail-closed：配置缺失即最小可见）",
                    properties.getDefaultPolicy());
            return DataScopeResult.ofPolicy(properties.getDefaultPolicy());
        }
        // 多对多：用户角色来自 sys_user_role 关联表（并集）。
        Set<Long> roleIds = port.roleIdsOfUser(userId);
        if (roleIds.isEmpty()) {
            // 无角色 => 按默认兜底策略（默认 SELF=fail-closed，最小可见）；显式配置 ALL 才不限制
            return DataScopeResult.ofPolicy(properties.getDefaultPolicy());
        }

        // ruleId -> scopeType；无规则 => 不限制。
        Map<Long, String> scopes = port.dataScopesOfRoles(roleIds, resourceCode);
        if (scopes.isEmpty()) {
            // 无规则 => 按默认兜底策略（默认 SELF=fail-closed，最小可见）
            return DataScopeResult.ofPolicy(properties.getDefaultPolicy());
        }

        // 主部门由 sys_user_org 中 is_primary=1 推导（多部门时取主部门用于 DEPT 类数据范围）。
        Long primaryOrgId = port.primaryOrgIdOfUser(userId);

        // 分类各角色规则的类型，并解析机构类规则对应的机构集合（DEPT/DEPT_AND_CHILD/CUSTOM 查库）。
        // 合并裁决抽离为纯函数 mergeRules，便于离线单测（见 DefaultDataScopeProviderTest）。
        Set<Long> orgIds = new HashSet<>();
        List<DataScopeType> ruleTypes = new ArrayList<>();
        for (Map.Entry<Long, String> e : scopes.entrySet()) {
            Long ruleId = e.getKey();
            DataScopeType type = safeType(e.getValue(), ruleId);
            ruleTypes.add(type);
            switch (type) {
                case DEPT -> {
                    if (primaryOrgId != null) {
                        orgIds.add(primaryOrgId);
                    }
                }
                case DEPT_AND_CHILD -> {
                    if (primaryOrgId != null) {
                        orgIds.add(primaryOrgId);
                        orgIds.addAll(port.childOrgIds(primaryOrgId));
                    }
                }
                case CUSTOM -> orgIds.addAll(port.customOrgIds(ruleId));
                default -> { /* ALL / SELF：无需解析机构 */ }
            }
        }
        return mergeRules(roleIds, primaryOrgId, ruleTypes, orgIds, resourceCode);
    }

    /**
     * 纯函数：按优先级合并多角色数据范围规则，产出最终可见范围。抽离为本方法以便离线单测（不依赖 DB）。
     *
     * <p>合并约定（与 {@link #resolve} 保持一致）：
     * <ul>
     *   <li>任一规则为 ALL ⇒ 整体不限（最高优先级）；</li>
     *   <li>否则任一规则为 SELF ⇒ 本人（优先于机构类规则）；</li>
     *   <li>其余机构类规则（DEPT / DEPT_AND_CHILD / CUSTOM）取并集；</li>
     *   <li>存在机构类规则却解析不出任何机构 ⇒ 降级到默认兜底策略（默认 SELF=fail-closed，最小可见）并告警。</li>
     * </ul>
     * 先收集全部规则类型再统一裁决，避免某角色的 SELF/ALL 提前 return 短路、漏掉另一角色的相反语义
     * （该缺陷在纯 N:N 多角色后才会暴露，单角色时代遍历顺序不影响结果）。
     *
     * @param roleIds        参与合并的角色 id 集合（仅用于告警信息）
     * @param primaryOrgId   用户主部门 id（可为 null）
     * @param ruleTypes      各角色规则的已解析类型列表
     * @param resolvedOrgIds 机构类规则已解析出的机构 id 并集（DEPT/DEPT_AND_CHILD/CUSTOM 查库结果）
     * @param resourceCode   资源码（仅用于告警信息）
     */
    DataScopeResult mergeRules(Set<Long> roleIds, Long primaryOrgId,
                              List<DataScopeType> ruleTypes,
                              Set<Long> resolvedOrgIds, String resourceCode) {
        boolean anyAll = false;
        boolean anySelf = false;
        boolean hasOrgRule = false;
        for (DataScopeType type : ruleTypes) {
            switch (type) {
                case ALL -> anyAll = true;
                case SELF -> anySelf = true;
                case DEPT, DEPT_AND_CHILD, CUSTOM -> hasOrgRule = true;
                default -> { /* 枚举扩展保护，理论上不会进入 */ }
            }
        }
        if (anyAll) {
            return DataScopeResult.all();
        }
        if (anySelf) {
            return DataScopeResult.self();
        }
        // 配了机构类规则但解析不出任何机构（如用户未挂部门）：降级到默认兜底策略（默认 SELF=fail-closed，
        // 最小可见）并告警；告警指向配置问题而非静默生效。如需「看不到任何数据」语义，将默认策略设为 NONE
        // （当前 DataScopeResult 仅支持 ALL/SELF，NONE 需下游谓词配合，故以 SELF 作为最小可见默认）。
        if (hasOrgRule && resolvedOrgIds.isEmpty()) {
            log.warn("数据权限机构类规则解析为空，按默认兜底策略 {} 处理；请检查用户是否已绑定部门或 CUSTOM 规则是否配置了机构。"
                    + "resourceCode={}, roleIds={}, orgId={}", properties.getDefaultPolicy(),
                    resourceCode, roleIds, primaryOrgId);
            return DataScopeResult.ofPolicy(properties.getDefaultPolicy());
        }
        return DataScopeResult.ofOrgs(resolvedOrgIds);
    }

    /**
     * 兜底 scope_type：按默认兜底策略映射。SELF 策略下脏/空 scope_type 回落 SELF（最小可见），
     * ALL 策略下回落 ALL（fail-open，兼容旧语义）。避免一条脏数据让整个角色查不到数据，
     * 同时把「配置缺失」收敛到统一的最小可见默认。
     */
    private DataScopeType defaultScopeType() {
        return properties.getDefaultPolicy() == DataScopeDefaultPolicy.SELF
                ? DataScopeType.SELF : DataScopeType.ALL;
    }

    /** scope_type 取值非法或为空时按兜底策略回落并告警，避免一条脏数据让整个角色查不到数据。 */
    private DataScopeType safeType(String s, Long ruleId) {
        if (s == null || s.isBlank()) {
            log.warn("数据权限规则 scope_type 为空，ruleId={}，按 ALL 处理", ruleId);
            return defaultScopeType();
        }
        try {
            return DataScopeType.valueOf(s);
        } catch (IllegalArgumentException e) {
            log.warn("数据权限规则 scope_type 非法：{}，ruleId={}，按 ALL 处理", s, ruleId);
            return defaultScopeType();
        }
    }
}
