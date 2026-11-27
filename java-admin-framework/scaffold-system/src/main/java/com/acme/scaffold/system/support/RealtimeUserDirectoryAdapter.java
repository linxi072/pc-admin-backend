package com.acme.scaffold.system.support;

import com.acme.scaffold.common.tenant.TenantEnforcement;
import com.acme.scaffold.mybatis.dsl.Condition;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.realtime.port.RealtimeUserDirectoryPort;
import com.acme.scaffold.system.user.domain.model.SysUser;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 以 system 用户表为实时推送管理提供租户内可见用户集合（realtime 端口的适配器实现）。 */
@Component
@RequiredArgsConstructor
public class RealtimeUserDirectoryAdapter implements RealtimeUserDirectoryPort {

    private static final SqlTables.TableRef USER = SqlTables.SYS_USER;
    private static final String ACTIVE = "ACTIVE";

    private final DSLContext dsl;

    @Override
    public Set<Long> userIdsInTenant(long tenantId) {
        Condition cond = SqlWriters.notDeleted(USER)
                .and(USER.field("status", String.class).eq(ACTIVE));
        // 强隔离下按显式租户收紧；未开启隔离时保持平台视图，避免改变既有单租户部署行为。
        if (TenantEnforcement.isEnabled()) {
            cond = cond.and(USER.field("tenant_id", Long.class).eq(tenantId));
        }
        return SqlWriters.fetchList(dsl, USER, SysUser.class, cond).stream()
                .map(SysUser::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
