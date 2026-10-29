package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.oauth2.domain.OAuth2TokenDO;
import com.acme.scaffold.common.api.dto.PageQuery;
import com.acme.scaffold.common.api.dto.PageResult;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * OAuth2 签发令牌注册表（M43 令牌治理）。
 *
 * <p>职责：在令牌签发时记录 jti + 元数据，提供吊销与「活动令牌」查询，
 * 供 {@link OAuth2TokenRevocationFilter}（运行时拒绝已吊销令牌）与管理端列表/吊销接口复用。
 * 实现见 {@code JooqOAuth2TokenRegistry}（jOOQ 动态 DSL，零新依赖）。</p>
 */
public interface OAuth2TokenRegistry {

    /**
     * 记录一次令牌签发。jti 必须与 JWT 的 id 声明严格一致（由调用方在签发时生成并透传）。
     *
     * @param tokenType 令牌类型：{@code CLIENT}（client_credentials）/ {@code USER}（password / authorization_code）
     */
    void recordIssuance(String jti, String clientId, Long userId, String username,
                        Set<String> scopes, String tokenType, LocalDateTime issuedAt, LocalDateTime expiresAt);

    /** 吊销指定 jti（status=REVOKED）。jti 不存在或已吊销时静默忽略。 */
    void revoke(String jti);

    /** jti 是否已被吊销（不存在视为未吊销，保证「未登记令牌」不误伤登录态）。 */
    boolean isRevoked(String jti);

    /** 分页查询活动令牌（status=ACTIVE 且未过期），可按 clientId / userId 过滤。 */
    PageResult<OAuth2TokenDO> listActive(PageQuery query, String clientId, Long userId);
}
