package com.acme.scaffold.system.permission.adapter;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.permission.DataScopeDataPort;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDataScopeDO;
import com.acme.scaffold.system.entity.SysRoleDataScopeOrgDO;
import com.acme.scaffold.system.entity.SysUserOrgDO;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link DataScopeDataPort} 的 system 侧适配器实现。
 *
 * <p>承载原 {@code DefaultDataScopeProvider} 中全部 jOOQ 直查逻辑（B5 将其从 security.permission 包剥离）。
 * 本类位于 {@code system} 包，允许 import {@code system.entity}，满足模块边界：
 * security.permission 不再直连系统域持久化对象。</p>
 */
@Component
@RequiredArgsConstructor
public class DataScopeDataAdapter implements DataScopeDataPort {

    private static final JooqTables.TableRef USER_ROLE = JooqTables.SYS_USER_ROLE;
    private static final JooqTables.TableRef USER_ORG = JooqTables.SYS_USER_ORG;

    private final DSLContext dsl;

    @Override
    public Set<Long> roleIdsOfUser(Long userId) {
        // 多对多：从 sys_user_role 关联表取该用户全部角色（并集）。
        List<SysUserRoleDO> rows = JooqWriters.fetchList(dsl, USER_ROLE, SysUserRoleDO.class,
                USER_ROLE.field("user_id", Long.class).eq(userId));
        if (rows.isEmpty()) {
            return Set.of();
        }
        return rows.stream().map(SysUserRoleDO::getRoleId).collect(Collectors.toSet());
    }

    @Override
    public Map<Long, String> dataScopesOfRoles(Set<Long> roleIds, String resourceCode) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE, SysRoleDataScopeDO.class,
                        DSL.and(JooqTables.SYS_ROLE_DATA_SCOPE.field("role_id", Long.class).in(roleIds),
                                JooqTables.SYS_ROLE_DATA_SCOPE.field("resource_code", String.class).eq(resourceCode)))
                .stream().collect(Collectors.toMap(
                        SysRoleDataScopeDO::getId,
                        SysRoleDataScopeDO::getScopeType,
                        (a, b) -> a));
    }

    @Override
    public Long primaryOrgIdOfUser(Long userId) {
        List<SysUserOrgDO> orgRows = JooqWriters.fetchList(dsl, USER_ORG, SysUserOrgDO.class,
                DSL.and(USER_ORG.field("user_id", Long.class).eq(userId),
                        USER_ORG.field("is_primary", Integer.class).eq(1)));
        return orgRows.isEmpty() ? null : orgRows.get(0).getOrgId();
    }

    @Override
    public Set<Long> childOrgIds(Long orgId) {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG), ancestorContains(orgId)))
                .stream().map(SysOrgDO::getId).collect(Collectors.toSet());
    }

    @Override
    public Set<Long> customOrgIds(Long ruleId) {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE_ORG, SysRoleDataScopeOrgDO.class,
                        JooqTables.SYS_ROLE_DATA_SCOPE_ORG.field("rule_id", Long.class).eq(ruleId))
                .stream().map(SysRoleDataScopeOrgDO::getOrgId).collect(Collectors.toSet());
    }

    /**
     * ancestors 是逗号分隔的物化路径（如 {@code 0,1,11}），必须按完整 ID 片段匹配。
     * 两侧补逗号后比较，保证命中的是完整 ID 而非子串（避免 orgId=1 命中 {@code ancestors='0,11,111'} 这类越权）。
     */
    private Condition ancestorContains(Long orgId) {
        return DSL.condition(DSL.concat(DSL.val(","),
                        JooqTables.SYS_ORG.field("ancestors", String.class), DSL.val(","))
                .like(DSL.val("%," + orgId + ",%")));
    }
}
