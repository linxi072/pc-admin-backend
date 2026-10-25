package com.acme.scaffold.system.openapi.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 更新开放平台客户端请求（部分更新，仅传入字段参与）。
 *
 * <p>client_id 不可变更（外部系统已按此绑定）；密钥变更走专用轮换端点，不走本 DTO。</p>
 */
public record UpdateOpenApiClientRequest(
        @Size(max = 128, message = "客户端名称不超过 128 字符") String clientName,
        @Size(max = 512, message = "授权范围不超过 512 字符") String scopes,
        String status,
        LocalDateTime expiredAt,
        @Size(max = 255, message = "备注不超过 255 字符") String remark) {
}
