package com.acme.scaffold.security.oauth2;

import java.util.Set;

/**
 * OAuth2 客户端快照（防腐层）。
 *
 * <p>由 {@code security.oauth2} 定义，承载令牌/授权端点所需的客户端标识、<b>已解析</b> scope 集合、
 * 授权码回调白名单与 PKCE 强制开关，避免 security 直接依赖 {@code system.openapi} 的
 * {@code OpenApiClientDO} / {@code OpenApiClientService}。</p>
 *
 * @param clientId     客户端标识
 * @param scopes       已解析的授权范围集合（由适配器经 {@code OpenApiClientRules.parseScopes} 归一）
 * @param redirectUris 已解析的授权码回调白名单（空集合=不约束，向后兼容存量客户端）
 * @param pkceRequired 是否强制 PKCE（授权码流程）
 */
public record OAuth2ClientView(String clientId, Set<String> scopes, Set<String> redirectUris, boolean pkceRequired) {
}
