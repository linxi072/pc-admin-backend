package com.acme.scaffold.system.openapi.dto;

/**
 * 客户端密钥一次性回传视图（创建 / 轮换成功时返回）。
 *
 * <p><b>明文仅此一次</b>：服务端不存储明文（仅存哈希），此后任何查询端点都不再返回；
 * 调用方须立即保存。丢失只能轮换。</p>
 *
 * @param clientId  客户端 ID
 * @param plainSecret 密钥明文（Base64 URL 无填充，43 字符）
 * @param rotatedAt  本次生效时间
 */
public record ClientSecretView(
        String clientId,
        String plainSecret,
        java.time.LocalDateTime rotatedAt) {
}
