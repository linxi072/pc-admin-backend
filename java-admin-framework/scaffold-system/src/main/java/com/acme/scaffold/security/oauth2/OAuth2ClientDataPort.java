package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.exception.BusinessException;

/**
 * OAuth2 客户端数据访问端口（防腐层）。
 *
 * <p>由 {@code security.oauth2} 定义，返回 security 自有 {@link OAuth2ClientView} 快照，
 * system 侧提供适配器委托 {@code OpenApiClientService}，消除 security 对 {@code system.openapi} 的直连
 * （B1/B5 同款模块边界约束）。</p>
 */
public interface OAuth2ClientDataPort {

    /**
     * 按 client_id / client_secret 认证客户端。
     *
     * @throws BusinessException 客户端不存在 / 密钥错误 / 停用 / 过期时（invalid_client）
     */
    OAuth2ClientView authenticate(String clientId, String clientSecret);

    /**
     * 按 client_id 取启用中的客户端。
     *
     * @throws BusinessException 客户端不存在 / 未启用时（invalid_client）
     */
    OAuth2ClientView getActive(String clientId);
}
