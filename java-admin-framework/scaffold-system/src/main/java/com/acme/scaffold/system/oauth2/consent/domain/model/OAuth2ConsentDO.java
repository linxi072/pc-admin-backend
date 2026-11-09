package com.acme.scaffold.system.oauth2.consent.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户同意(consent)实体（对应 {@code sys_oauth2_consent}，V68 #900）。
 *
 * <p>同一用户+客户端仅保留最新一条已批准同意（审批幂等）；拒绝不落库。</p>
 */
@Data
public class OAuth2ConsentDO {

    private Long id;

    /** 租户 ID（默认 0，与全库多租户约定一致）。 */
    private Long tenantId = 0L;

    private Long userId;

    private String clientId;

    /** 已批准授权范围（规范化逗号分隔串）。 */
    private String scopes;

    /** 状态：APPROVED。 */
    private String status;

    /** 过期时间（可空）。 */
    private LocalDateTime expiresAt;

    private Long version;

    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
