package com.acme.scaffold.security.permission;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDataScopeDO;
import com.acme.scaffold.system.entity.SysRoleDataScopeOrgDO;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import com.acme.scaffold.system.entity.SysUserOrgDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认数据权限解析：基于角色数据范围规则（sys_role_data_scope / sys_role_data_scope_org）计算可见范围。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
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

    private final DSLContext dsl;

    @Override
    public DataScopeResult resolve(Long userId, String resourceCode) {
        if (userId == null) {
            return DataScopeResult.all();
        }
        // 多对多：用户角色来自 sys_user_role 关联表（并集）。
        List<SysUserRoleDO> roleRows = JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ROLE, SysUserRoleDO.class,
                JooqTables.SYS_USER_ROLE.field("user_id", Long.class).eq(userId));
        if (roleRows.isEmpty()) {
            return DataScopeResult.all(); // 无角色 => 按不限制兜底（与 V6 语义一致）
        }
        Set<Long> roleIds = roleRows.stream().map(SysUserRoleDO::getRoleId).collect(Collectors.toSet());

        List<SysRoleDataScopeDO> rules = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE,
                SysRoleDataScopeDO.class,
                DSL.and(JooqTables.SYS_ROLE_DATA_SCOPE.field("role_id", Long.class).in(roleIds),
                        JooqTables.SYS_ROLE_DATA_SCOPE.field("resource_code", String.class).eq(resourceCode)));
        if (rules.isEmpty()) {
            return DataScopeResult.all(); // 无规则 => 不限制
        }

        // 主部门由 sys_user_org 中 is_primary=1 推导（多部门时取主部门用于 DEPT 类数据范围）
        List<SysUserOrgDO> orgRows = JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ORG, SysUserOrgDO.class,
                DSL.and(JooqTables.SYS_USER_ORG.field("user_id", Long.class).eq(userId),
                        JooqTables.SYS_USER_ORG.field("is_primary", Integer.class).eq(1)));
        Long primaryOrgId = orgRows.isEmpty() ? null : orgRows.get(0).getOrgId();

        // 分类各角色规则的类型，并解析机构类规则对应的机构集合（DEPT/DEPT_AND_CHILD/CUSTOM 需查库）。
        // 合并裁决抽离为纯函数 mergeRules，便于离线单测（见 DefaultDataScopeProviderTest）。
        Set<Long> orgIds = new HashSet<>();
        List<DataScopeType> ruleTypes = new ArrayList<>();
        for (SysRoleDataScopeDO rule : rules) {
            DataScopeType type = safeType(rule.getScopeType(), rule.getId());
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
                        orgIds.addAll(childOrgIds(primaryOrgId));
                    }
                }
                case CUSTOM -> orgIds.addAll(customOrgIds(rule.getId()));
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

    /**
     * 取某机构的全部下级机构。
     *
     * <p><b>ancestors 是逗号分隔的物化路径（如 {@code 0,1,11}），必须按完整 ID 片段匹配。</b>
     * 原实现用 {@code LIKE '%orgId%'}，会导致 orgId=1 命中 {@code ancestors='0,11,111'}
     * —— 即 id=1 的部门能看到 id=11、111 这类无关机构，属越权。
     */
    private Set<Long> childOrgIds(Long orgId) {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG), ancestorContains(orgId)))
                .stream().map(SysOrgDO::getId).collect(Collectors.toSet());
    }

    /**
     * ancestors 片段匹配条件：两侧补逗号后比较，保证命中的是完整 ID 而非子串。
     * 生成 SQL 形如 {@code CONCAT(',', ancestors, ',') LIKE '%,1,%'}。
     */
    private Condition ancestorContains(Long orgId) {
        return DSL.condition(DSL.concat(DSL.val(","),
                        JooqTables.SYS_ORG.field("ancestors", String.class), DSL.val(","))
                .like(DSL.val("%," + orgId + ",%")));
    }

    private Set<Long> customOrgIds(Long ruleId) {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE_ORG, SysRoleDataScopeOrgDO.class,
                        JooqTables.SYS_ROLE_DATA_SCOPE_ORG.field("rule_id", Long.class).eq(ruleId)).stream()
                .map(SysRoleDataScopeOrgDO::getOrgId).collect(Collectors.toSet());
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
