package com.acme.scaffold.security.token;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysRefreshTokenDO {

    private Long id;

    private Long tenantId = 0L;
    private Long userId;
    private String clientId;
    private String sessionId;
    private String familyId;
    private String tokenHash;
    private String deviceId;
    private String userAgent;
    private String ipAddress;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private String revokeReason;
    private Long replacedById;
    private Integer reuseDetected = 0;

    private Integer version = 0;
}
