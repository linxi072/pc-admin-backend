package com.acme.scaffold.security.permission;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Override
    public DataScopeResult resolve(Long userId, String resourceCode) {
        if (userId == null) {
            return DataScopeResult.all();
        }
        // 多对多：用户角色来自 sys_user_role 关联表（并集）。
        Set<Long> roleIds = port.roleIdsOfUser(userId);
        if (roleIds.isEmpty()) {
            return DataScopeResult.all(); // 无角色 => 按不限制兜底（与 V6 语义一致）
        }

        // ruleId -> scopeType；无规则 => 不限制。
        Map<Long, String> scopes = port.dataScopesOfRoles(roleIds, resourceCode);
        if (scopes.isEmpty()) {
            return DataScopeResult.all(); // 无规则 => 不限制
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
     *   <li>存在机构类规则却解析不出任何机构 ⇒ 降级「不限制」并告警（故意 fail-open，可见性优先）。</li>
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
        // 配了机构类规则但解析不出任何机构（如用户未挂部门）：降级为「不限制」并告警（故意 fail-open，
        // 可见性优先于安全性），否则该角色会一条数据都看不到；告警指向配置问题而非静默生效。
        // 若产品要求「看不到任何数据」语义，请在此改为 return DataScopeResult.self()。
        if (hasOrgRule && resolvedOrgIds.isEmpty()) {
            log.warn("数据权限解析为空，按不限制处理；请检查用户是否已绑定部门或 CUSTOM 规则是否配置了机构。"
                    + "resourceCode={}, roleIds={}, orgId={}", resourceCode, roleIds, primaryOrgId);
            return DataScopeResult.all();
        }
        return DataScopeResult.ofOrgs(resolvedOrgIds);
    }

    /** scope_type 取值非法或为空时按 ALL 兜底并告警，避免一条脏数据让整个角色查不到数据。 */
    private DataScopeType safeType(String s, Long ruleId) {
        if (s == null || s.isBlank()) {
            log.warn("数据权限规则 scope_type 为空，ruleId={}，按 ALL 处理", ruleId);
            return DataScopeType.ALL;
        }
        try {
            return DataScopeType.valueOf(s);
        } catch (IllegalArgumentException e) {
            log.warn("数据权限规则 scope_type 非法：{}，ruleId={}，按 ALL 处理", s, ruleId);
            return DataScopeType.ALL;
        }
    }
}
