package com.acme.scaffold.system.openapi.domain;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 开放平台客户端实体（对应 {@code sys_openapi_client}，V48 API 开放平台客户端注册与密钥治理）。
 *
 * <p>密钥安全：{@code clientSecretHash} 为 DelegatingPasswordEncoder 哈希值，明文仅在创建/轮换
 * 成功时一次性返回，任何持久化与查询路径都不落明文。</p>
 */
@Data
public class OpenApiClientDO {

    private Long id;

    /** 租户 ID（默认 0，与全库多租户约定一致）。 */
    private Long tenantId = 0L;

    /** 客户端 ID（租户内唯一，外部系统凭此标识身份）。 */
    private String clientId;

    /** 客户端名称（人类可读）。 */
    private String clientName;

    /** 客户端密钥哈希（DelegatingPasswordEncoder）。 */
    private String clientSecretHash;

    /** 授权范围（逗号分隔，如 read,write）。 */
    private String scopes;

    /** 状态：ENABLED / DISABLED。 */
    private String status;

    /** 最近一次密钥轮换时间。 */
    private LocalDateTime secretRotatedAt;

    /** 可选过期时间（到期后应停用，具体拦截策略待协议级开放时落地）。 */
    private LocalDateTime expiredAt;

    private String remark;

    private Long version;

    private Integer deleted;

    private String createdBy;

    private LocalDateTime createdAt;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
