package com.acme.scaffold.system.user.adapter;

import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.common.validation.RecordStatus;
import com.acme.scaffold.security.user.UserAccount;
import com.acme.scaffold.security.user.UserAccountDataPort;
import com.acme.scaffold.security.user.UserAccountPatch;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DSL;
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
    private static final SqlTables.TableRef USER = SqlTables.SYS_USER;

    private final DSLContext dsl;

    @Override
    public UserAccount loadByUsername(String username) {
        if (username == null) {
            return null;
        }
        SysUserDO u = SqlWriters.fetchOne(dsl, USER, SysUserDO.class,
                DSL.and(SqlWriters.notDeleted(USER),
                        USER.field("username", String.class).eq(username)));
        return toAccount(u);
    }

    @Override
    public UserAccount loadById(Long id) {
        if (id == null) {
            return null;
        }
        SysUserDO u = SqlWriters.fetchById(dsl, USER, SysUserDO.class, id);
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

    @Override
    public void updateById(Long id, UserAccountPatch patch) {
        if (id == null || patch == null) {
            return;
        }
        // 仅非空字段参与更新（SqlWriters.updateById 跳过 null），与 AuthService 的部分补丁语义一致
        SysUserDO p = new SysUserDO();
        p.setPasswordHash(patch.passwordHash());
        p.setPasswordChangedAt(patch.passwordChangedAt());
        p.setPasswordExpired(patch.passwordExpired());
        p.setFailedLoginCount(patch.failedLoginCount());
        p.setLockedUntil(patch.lockedUntil());
        p.setTokenVersion(patch.tokenVersion());
        p.setLastLoginAt(patch.lastLoginAt());
        // 状态经 F1 通用归一器白名单校验（仅非空时；null 表示本次补丁不改状态，保持既有语义）。
        // 安全层仅产出 ACTIVE/DISABLED/LOCKED，归一为 no-op；异常值在此即被拦截（纵深防御）
        if (patch.status() != null) {
            p.setStatus(RecordStatus.normalize(patch.status()));
        }
        SqlWriters.updateById(dsl, USER, id, p);
    }
}
