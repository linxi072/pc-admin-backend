package com.acme.scaffold.system.oauth2.consent;

import com.acme.scaffold.mybatis.dsl.SqlTables;
import com.acme.scaffold.mybatis.dsl.SqlWriters;
import com.acme.scaffold.security.oauth2.OAuth2ConsentDataPort;
import com.acme.scaffold.security.oauth2.OAuth2ConsentInfo;
import com.acme.scaffold.system.oauth2.consent.domain.OAuth2ConsentDO;
import lombok.RequiredArgsConstructor;
import com.acme.scaffold.mybatis.dsl.DSLContext;
import com.acme.scaffold.mybatis.dsl.DSL;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * {@link OAuth2ConsentDataPort} 的 system 侧适配器：经 jOOQ 读写 {@code sys_oauth2_consent}（V68 #900）。
 */
@Component
@RequiredArgsConstructor
public class OAuth2ConsentAdapter implements OAuth2ConsentDataPort {

    private static final String STATUS_APPROVED = "APPROVED";

    private final DSLContext dsl;

    @Override
    public Optional<OAuth2ConsentInfo> findLatestApproved(Long userId, String clientId) {
        OAuth2ConsentDO d = SqlWriters.fetchOne(dsl, SqlTables.SYS_OAUTH2_CONSENT, OAuth2ConsentDO.class,
                DSL.and(SqlWriters.notDeleted(SqlTables.SYS_OAUTH2_CONSENT),
                        SqlTables.SYS_OAUTH2_CONSENT.field("user_id", Long.class).eq(userId),
                        SqlTables.SYS_OAUTH2_CONSENT.field("client_id", String.class).eq(clientId),
                        SqlTables.SYS_OAUTH2_CONSENT.field("status", String.class).eq(STATUS_APPROVED)));
        if (d == null) {
            return Optional.empty();
        }
        return Optional.of(new OAuth2ConsentInfo(d.getScopes(), d.getExpiresAt()));
    }

    @Override
    public void recordApproved(Long userId, String clientId, String scopes) {
        OAuth2ConsentDO existing = SqlWriters.fetchOne(dsl, SqlTables.SYS_OAUTH2_CONSENT, OAuth2ConsentDO.class,
                DSL.and(SqlWriters.notDeleted(SqlTables.SYS_OAUTH2_CONSENT),
                        SqlTables.SYS_OAUTH2_CONSENT.field("user_id", Long.class).eq(userId),
                        SqlTables.SYS_OAUTH2_CONSENT.field("client_id", String.class).eq(clientId)));
        if (existing == null) {
            OAuth2ConsentDO d = new OAuth2ConsentDO();
            d.setUserId(userId);
            d.setClientId(clientId);
            d.setScopes(scopes);
            d.setStatus(STATUS_APPROVED);
            SqlWriters.insert(dsl, SqlTables.SYS_OAUTH2_CONSENT, d);
        } else {
            OAuth2ConsentDO patch = new OAuth2ConsentDO();
            patch.setScopes(scopes);
            patch.setStatus(STATUS_APPROVED);
            SqlWriters.updateById(dsl, SqlTables.SYS_OAUTH2_CONSENT, existing.getId(), patch);
        }
    }
}
