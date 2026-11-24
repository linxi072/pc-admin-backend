package com.acme.scaffold.system.permission.adapter;

import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSL;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.security.permission.DataScopeDataPort;
import com.acme.scaffold.system.org.domain.model.SysOrg;
import com.acme.scaffold.system.role.domain.model.SysRoleDataScope;
import com.acme.scaffold.system.role.domain.model.SysRoleDataScopeOrg;
import com.acme.scaffold.system.user.domain.model.SysUserOrg;
import java.util.List;
import java.util.Map;

import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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

    private static final SqlTables.TableRef USER_ORG = SqlTables.SYS_USER_ORG;

    private final DSLContext dsl;

    @Override
    public Set<Long> roleIdsOfUser(Long userId) {
        return UserRoleSupport.roleIdsOfUser(dsl, userId);
    }

    @Override
    public Map<Long, String> dataScopesOfRoles(Set<Long> roleIds, String resourceCode) {
        if (roleIds.isEmpty()) {
            return Map.of();
        }
        return SqlWriters.fetchList(dsl, SqlTables.SYS_ROLE_DATA_SCOPE, SysRoleDataScope.class,
                        DSL.and(SqlTables.SYS_ROLE_DATA_SCOPE.field("role_id", Long.class).in(roleIds),
                                SqlTables.SYS_ROLE_DATA_SCOPE.field("resource_code", String.class).eq(resourceCode)))
                .stream().collect(Collectors.toMap(
                        SysRoleDataScope::getId,
                        SysRoleDataScope::getScopeType,
                        (a, b) -> a));
    }

    @Override
    public Long primaryOrgIdOfUser(Long userId) {
        List<SysUserOrg> orgRows = SqlWriters.fetchList(dsl, USER_ORG, SysUserOrg.class,
                DSL.and(USER_ORG.field("user_id", Long.class).eq(userId),
                        USER_ORG.field("is_primary", Integer.class).eq(1)));
        return orgRows.isEmpty() ? null : orgRows.get(0).getOrgId();
    }

    @Override
    public Set<Long> childOrgIds(Long orgId) {
        return SqlWriters.fetchList(dsl, SqlTables.SYS_ORG, SysOrg.class,
                        DSL.and(SqlWriters.notDeleted(SqlTables.SYS_ORG), ancestorContains(orgId)))
                .stream().map(SysOrg::getId).collect(Collectors.toSet());
    }

    @Override
    public Set<Long> customOrgIds(Long ruleId) {
        return SqlWriters.fetchList(dsl, SqlTables.SYS_ROLE_DATA_SCOPE_ORG, SysRoleDataScopeOrg.class,
                        SqlTables.SYS_ROLE_DATA_SCOPE_ORG.field("rule_id", Long.class).eq(ruleId))
                .stream().map(SysRoleDataScopeOrg::getOrgId).collect(Collectors.toSet());
    }

    /**
     * ancestors 是逗号分隔的物化路径（如 {@code 0,1,11}），必须按完整 ID 片段匹配。
     * 两侧补逗号后比较，保证命中的是完整 ID 而非子串（避免 orgId=1 命中 {@code ancestors='0,11,111'} 这类越权）。
     */
    private Condition ancestorContains(Long orgId) {
        return DSL.condition(DSL.concat(DSL.val(","),
                        SqlTables.SYS_ORG.field("ancestors", String.class), DSL.val(","))
                .like(DSL.val("%," + orgId + ",%")));
    }
}
