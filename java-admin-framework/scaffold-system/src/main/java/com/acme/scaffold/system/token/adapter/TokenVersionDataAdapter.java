package com.acme.scaffold.system.token.adapter;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.token.service.TokenVersionDataPort;
import com.acme.scaffold.system.entity.SysUserRoleDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.acme.scaffold.jooqgen.Tables;

/**
 * {@link TokenVersionDataPort} 的 system 侧适配器实现。
 *
 * <p>承载原 {@code TokenVersionService#bumpRoleHolders} 中对 {@code sys_user_role} 的反查，
 * 将 security.token.service 与 {@code system.entity} 解耦（B1/B5 同款防腐层约束）。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenVersionDataAdapter implements TokenVersionDataPort {

    private static final JooqTables.TableRef USER_ROLE = JooqTables.toTableRef(Tables.SYS_USER_ROLE);

    private final DSLContext dsl;

    @Override
    public List<Long> userIdsByRoleIds(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return JooqWriters.fetchList(dsl, USER_ROLE, SysUserRoleDO.class,
                        USER_ROLE.field("role_id", Long.class).in(roleIds)).stream()
                .map(SysUserRoleDO::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }
}
