package com.acme.scaffold.security.permission;

import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.system.entity.SysOrgDO;
import com.acme.scaffold.system.entity.SysRoleDataScopeDO;
import com.acme.scaffold.system.entity.SysRoleDataScopeOrgDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 默认数据权限解析：基于角色数据范围规则（sys_role_data_scope / sys_role_data_scope_org）计算可访问机构集合。
 * 返回空集合表示“不限制（ALL）”。持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultDataScopeProvider implements DataScopeProvider {

    private final DSLContext dsl;

    @Override
    public Set<Long> resolveOrgIds(Long userId, String resourceCode) {
        Set<Long> roleIds = JooqWriters.fetchList(dsl, JooqTables.SYS_USER_ROLE, SysUserRoleDO.class,
                        JooqTables.SYS_USER_ROLE.field("user_id", Long.class).eq(userId)).stream()
                .map(SysUserRoleDO::getRoleId).collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        List<SysRoleDataScopeDO> rules = JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE,
                SysRoleDataScopeDO.class,
                DSL.and(JooqTables.SYS_ROLE_DATA_SCOPE.field("role_id", Long.class).in(roleIds),
                        JooqTables.SYS_ROLE_DATA_SCOPE.field("resource_code", String.class).eq(resourceCode)));
        if (rules.isEmpty()) {
            return Set.of(); // 无规则 => 不限制
        }

        Set<Long> result = new HashSet<>();
        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, userId);
        Long primaryOrgId = user == null ? null : user.getPrimaryOrgId();

        for (SysRoleDataScopeDO rule : rules) {
            DataScopeType type = safeType(rule.getScopeType());
            switch (type) {
                case ALL -> {
                    return Set.of(); // 任一角色拥有全部权限即放行
                }
                case SELF, DEPT -> {
                    if (primaryOrgId != null) {
                        result.add(primaryOrgId);
                    }
                }
                case DEPT_AND_CHILD -> {
                    if (primaryOrgId != null) {
                        result.add(primaryOrgId);
                        result.addAll(childOrgIds(primaryOrgId));
                    }
                }
                case CUSTOM -> result.addAll(customOrgIds(rule.getId()));
            }
        }
        return result;
    }

    private Set<Long> childOrgIds(Long orgId) {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ORG, SysOrgDO.class,
                        DSL.and(JooqWriters.notDeleted(JooqTables.SYS_ORG),
                                JooqTables.SYS_ORG.field("ancestors", String.class).like("%" + orgId + "%")))
                .stream().map(SysOrgDO::getId).collect(Collectors.toSet());
    }

    private Set<Long> customOrgIds(Long ruleId) {
        return JooqWriters.fetchList(dsl, JooqTables.SYS_ROLE_DATA_SCOPE_ORG, SysRoleDataScopeOrgDO.class,
                        JooqTables.SYS_ROLE_DATA_SCOPE_ORG.field("rule_id", Long.class).eq(ruleId)).stream()
                .map(SysRoleDataScopeOrgDO::getOrgId).collect(Collectors.toSet());
    }

    private DataScopeType safeType(String s) {
        try {
            return DataScopeType.valueOf(s);
        } catch (Exception e) {
            return DataScopeType.ALL;
        }
    }
}
