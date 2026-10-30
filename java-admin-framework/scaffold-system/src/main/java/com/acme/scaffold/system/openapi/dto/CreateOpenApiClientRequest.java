package com.acme.scaffold.system.openapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建开放平台客户端请求。
 *
 * <p>结构校验由 {@link com.acme.scaffold.common.oauth2.OpenApiClientRules#validate} 兜底；
 * 此处以 Bean Validation 做入参层面的快速失败（非空与长度上限）。scopes 逗号分隔（如 {@code read,write}），
 * 空值回落默认 {@code read}。</p>
 */
public record CreateOpenApiClientRequest(
        @NotBlank @Size(max = 64, message = "客户端 ID 不超过 64 字符") String clientId,
        @NotBlank @Size(max = 128, message = "客户端名称不超过 128 字符") String clientName,
        @Size(max = 512, message = "授权范围不超过 512 字符") String scopes,
        String status,
        LocalDateTime expiredAt,
        @Size(max = 255, message = "备注不超过 255 字符") String remark) {
}
