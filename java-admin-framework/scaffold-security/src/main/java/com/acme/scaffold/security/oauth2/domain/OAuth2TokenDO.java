package com.acme.scaffold.security.oauth2.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * OAuth2 签发令牌注册记录（M43 令牌治理）。
 *
 * <p>每次令牌签发（client_credentials / password / authorization_code）落一条 ACTIVE 记录；
 * 吊销将 status 置为 REVOKED，由 {@link com.acme.scaffold.security.oauth2.OAuth2TokenRevocationFilter}
 * 在请求期实时拒绝。字段与 V64 迁移脚本严格一致。</p>
 */
@Data
public class OAuth2TokenDO {

    private Long id;

    private Long tenantId = 0L;
    private String jti;
    private String clientId;
    private Long userId;
    private String username;
    private String scope;
    private String tokenType;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private String status;
    private Long version = 0L;
    private Integer deleted = 0;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
