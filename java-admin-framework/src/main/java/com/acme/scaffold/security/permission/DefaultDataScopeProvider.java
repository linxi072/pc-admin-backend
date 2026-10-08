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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认数据权限解析：基于角色数据范围规则（sys_role_data_scope / sys_role_data_scope_org）计算可见范围。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 *
 * <p>规则合并约定（V6 收敛后用户仅绑定单个角色，故只有一组规则）：
 * <ul>
 *   <li>任一规则为 ALL => 整体 ALL；</li>
 *   <li>SELF 优先于机构类规则（更严格者胜），命中即短路；</li>
 *   <li>其余机构类规则取并集。</li>
 * </ul>
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

        Set<Long> orgIds = new HashSet<>();
        // 主部门由 sys_user_org 中 is_primary=1 推导（多部门时取主部门用于 DEPT 类数据范围）
        List<SysUserOrgDO> orgRows = JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ORG, SysUserOrgDO.class,
                DSL.and(JooqTables.SYS_USER_ORG.field("user_id", Long.class).eq(userId),
                        JooqTables.SYS_USER_ORG.field("is_primary", Integer.class).eq(1)));
        Long primaryOrgId = orgRows.isEmpty() ? null : orgRows.get(0).getOrgId();
        boolean hasOrgRule = false;

        for (SysRoleDataScopeDO rule : rules) {
            DataScopeType type = safeType(rule.getScopeType(), rule.getId());
            switch (type) {
                case ALL -> {
                    return DataScopeResult.all(); // 角色拥有全部数据即放行
                }
                // SELF 按本人过滤而非机构，优先于机构类规则，命中即短路
                case SELF -> {
                    return DataScopeResult.self();
                }
                case DEPT -> {
                    hasOrgRule = true;
                    if (primaryOrgId != null) {
                        orgIds.add(primaryOrgId);
                    }
                }
                case DEPT_AND_CHILD -> {
                    hasOrgRule = true;
                    if (primaryOrgId != null) {
                        orgIds.add(primaryOrgId);
                        orgIds.addAll(childOrgIds(primaryOrgId));
                    }
                }
                case CUSTOM -> {
                    hasOrgRule = true;
                    orgIds.addAll(customOrgIds(rule.getId()));
                }
            }
        }
        // 配了机构类规则但解析不出任何机构（如用户未挂部门）：降级为不限制并告警，
        // 否则该角色会一条数据都看不到；告警指向配置问题而非静默生效。
        if (hasOrgRule && orgIds.isEmpty()) {
            log.warn("数据权限解析为空，按不限制处理；请检查用户是否已绑定部门或 CUSTOM 规则是否配置了机构。"
                    + "resourceCode={}, roleIds={}, orgId={}", resourceCode, roleIds, primaryOrgId);
            return DataScopeResult.all();
        }
        return DataScopeResult.ofOrgs(orgIds);
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
