package com.acme.scaffold.system.user.adapter;

import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.user.UserAccount;
import com.acme.scaffold.security.user.UserAccountDataPort;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Component;

/**
 * {@link UserAccountDataPort} 的 system 侧适配器实现。
 *
 * <p>承载原 security 包中对 {@code sys_user} 的全部 jOOQ 直查（B1/B5 同款解耦：
 * security 不再直连 system.entity）。本类位于 {@code system} 包，允许 import {@code system.entity}。</p>
 */
@Component
@RequiredArgsConstructor
public class UserAccountDataAdapter implements UserAccountDataPort {

    private final DSLContext dsl;

    @Override
    public UserAccount loadByUsername(String username) {
        if (username == null) {
            return null;
        }
        SysUserDO u = JooqWriters.fetchOne(dsl, JooqTables.SYS_USER, SysUserDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("username", String.class).eq(username)));
        return toAccount(u);
    }

    @Override
    public UserAccount loadById(Long id) {
        if (id == null) {
            return null;
        }
        SysUserDO u = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, id);
        return toAccount(u);
    }

    private UserAccount toAccount(SysUserDO u) {
        if (u == null) {
            return null;
        }
        return new UserAccount(u.getId(), u.getUsername(), u.getDisplayName(), u.getTenantId(),
                u.getStatus(), u.getPasswordHash(), u.getTokenVersion(), u.getPasswordExpired(),
                u.getMobile(), u.getEmail(), u.getLockedUntil(), u.getFailedLoginCount(), u.getLastLoginAt());
    }
}
