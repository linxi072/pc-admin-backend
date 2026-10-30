package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.oauth2.domain.OAuth2TokenDO;
import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.jooqgen.Tables;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 jOOQ 动态 DSL 的 OAuth2 令牌注册表实现（M43）。
 *
 * <p>复用既有 {@link JooqWriters}（自动填充 id / tenant_id / 时间字段、逻辑删除、分页），
 * 不引入任何新依赖；表元数据来自 {@link JooqTables#SYS_OAUTH2_TOKEN}。</p>
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class JooqOAuth2TokenRegistry implements OAuth2TokenRegistry {

    private final DSLContext dsl;

    private static final JooqTables.TableRef OT = JooqTables.toTableRef(Tables.SYS_OAUTH2_TOKEN);

    private static final String STATUS_ACTIVE = "ACTIVE";

    @Override
    public void recordIssuance(String jti, String clientId, Long userId, String username,
                              Set<String> scopes, String tokenType, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        OAuth2TokenDO token = new OAuth2TokenDO();
        token.setJti(jti);
        token.setClientId(clientId);
        token.setUserId(userId);
        token.setUsername(username);
        token.setScope(joinScopes(scopes));
        token.setTokenType(tokenType);
        token.setIssuedAt(issuedAt);
        token.setExpiresAt(expiresAt);
        token.setStatus(STATUS_ACTIVE);
        JooqWriters.insert(dsl, OT, token);
        log.info("OAuth2 令牌已登记 jti={} clientId={} type={} userId={}", jti, clientId, tokenType, userId);
    }

    @Override
    public void revoke(String jti) {
        OAuth2TokenDO existing = JooqWriters.fetchOne(dsl, OT, OAuth2TokenDO.class,
                activeByJti(jti));
        if (existing == null) {
            return;
        }
        OAuth2TokenDO update = new OAuth2TokenDO();
        update.setStatus("REVOKED");
        JooqWriters.updateById(dsl, OT, existing.getId(), update);
        log.info("OAuth2 令牌已吊销 jti={}", jti);
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        OAuth2TokenDO token = JooqWriters.fetchOne(dsl, OT, OAuth2TokenDO.class,
                activeByJti(jti));
        // 查不到（未签发 / 已吊销 / 已逻辑删除）→ 不视为「已吊销」，避免误伤登录态
        return token != null && !STATUS_ACTIVE.equals(token.getStatus());
    }

    @Override
    public PageResult<OAuth2TokenDO> listActive(PageQuery query, String clientId, Long userId) {
        Condition cond = JooqWriters.notDeleted(OT)
                .and(OT.field("status", String.class).eq(STATUS_ACTIVE))
                .and(OT.field("expires_at", LocalDateTime.class).gt(LocalDateTime.now()));
        if (clientId != null && !clientId.isBlank()) {
            cond = cond.and(OT.field("client_id", String.class).eq(clientId));
        }
        if (userId != null) {
            cond = cond.and(OT.field("user_id", Long.class).eq(userId));
        }
        List<OAuth2TokenDO> records = JooqWriters.fetchList(dsl, OT, OAuth2TokenDO.class,
                cond, OT.field("expires_at", LocalDateTime.class).desc());
        long total = JooqWriters.count(dsl, OT, cond);
        int from = (int) Math.min(query.offset(), records.size());
        int to = (int) Math.min(from + query.limit(), records.size());
        List<OAuth2TokenDO> pageRecords = records.subList(from, to);
        return new PageResult<>(query.page(), query.size(), total, pageRecords);
    }

    /** 活动（未吊销且未过期）且 jti 匹配的查询条件。 */
    private static Condition activeByJti(String jti) {
        return JooqWriters.notDeleted(OT)
                .and(OT.field("jti", String.class).eq(jti));
    }

    private static String joinScopes(Set<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return null;
        }
        // 保持确定性顺序，便于审计与比对
        return new LinkedHashSet<>(scopes).stream().sorted().reduce((a, b) -> a + " " + b).orElse(null);
    }
}
