package com.acme.scaffold.system.token.adapter;

import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.security.token.service.TokenVersionDataPort;
import com.acme.scaffold.system.user.domain.model.SysUserRoleDO;
import java.util.List;
import java.util.Set;

import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link TokenVersionDataPort} 的 system 侧适配器实现。
 *
 * <p>承载原 {@code TokenVersionService#bumpRoleHolders} 中对 {@code sys_user_role} 的反查，
 * 将 security.token.service 与 {@code system.entity} 解耦（B1/B5 同款防腐层约束）。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenVersionDataAdapter implements TokenVersionDataPort {

    private static final SqlTables.TableRef USER_ROLE = SqlTables.SYS_USER_ROLE;

    private final DSLContext dsl;

    @Override
    public List<Long> userIdsByRoleIds(Set<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return SqlWriters.fetchList(dsl, USER_ROLE, SysUserRoleDO.class,
                        USER_ROLE.field("role_id", Long.class).in(roleIds)).stream()
                .map(SysUserRoleDO::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }
}
