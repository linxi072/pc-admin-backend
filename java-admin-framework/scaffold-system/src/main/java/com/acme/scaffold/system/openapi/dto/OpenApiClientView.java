package com.acme.scaffold.system.openapi.dto;

import com.acme.scaffold.system.openapi.domain.OpenApiClientDO;

import java.time.LocalDateTime;

/**
 * 开放平台客户端视图（对应 {@code sys_openapi_client}）。
 *
 * <p><b>不含任何密钥字段</b>：哈希不回放，明文仅在创建/轮换时经 {@link ClientSecretView} 一次性返回。</p>
 *
 * @param id             客户端记录 ID
 * @param clientId       客户端 ID（租户内唯一）
 * @param clientName     客户端名称
 * @param scopes         授权范围（规范化逗号分隔串）
 * @param status         状态：ENABLED / DISABLED
 * @param secretRotatedAt 最近一次密钥轮换时间
 * @param expiredAt      过期时间（可空）
 * @param remark         备注
 * @param createdAt      创建时间
 * @param updatedAt      更新时间
 */
public record OpenApiClientView(
        Long id,
        String clientId,
        String clientName,
        String scopes,
        String status,
        LocalDateTime secretRotatedAt,
        LocalDateTime expiredAt,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /** 由持久化对象构造视图（null 返回 null）。 */
    public static OpenApiClientView of(OpenApiClientDO d) {
        if (d == null) {
            return null;
        }
        return new OpenApiClientView(
                d.getId(), d.getClientId(), d.getClientName(), d.getScopes(), d.getStatus(),
                d.getSecretRotatedAt(), d.getExpiredAt(), d.getRemark(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
