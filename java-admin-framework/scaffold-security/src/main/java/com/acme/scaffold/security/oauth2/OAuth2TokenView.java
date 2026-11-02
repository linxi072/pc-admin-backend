package com.acme.scaffold.security.oauth2;

import java.time.LocalDateTime;

/**
 * OAuth2 活动令牌视图（M43 令牌管理前端用）。
 *
 * <p>由 {@link OAuth2TokenAdminService} 从 {@link com.acme.scaffold.security.oauth2.domain.OAuth2TokenDO} 映射得到，仅暴露管理面所需的
 * 只读字段（jti / 客户端 / 用户 / 作用域 / 类型 / 时间 / 状态），不含敏感声明。</p>
 */
public record OAuth2TokenView(
        String jti,
        String clientId,
        Long userId,
        String username,
        String scope,
        String tokenType,
        LocalDateTime issuedAt,
        LocalDateTime expiresAt,
        String status) {
}
