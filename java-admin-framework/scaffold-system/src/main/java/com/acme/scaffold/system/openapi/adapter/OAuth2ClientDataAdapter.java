package com.acme.scaffold.system.openapi.adapter;

import com.acme.scaffold.common.oauth2.OpenApiClientRules;
import com.acme.scaffold.security.oauth2.OAuth2ClientDataPort;
import com.acme.scaffold.security.oauth2.OAuth2ClientView;
import com.acme.scaffold.system.openapi.application.OpenApiClientService;
import com.acme.scaffold.system.openapi.domain.model.OpenApiClientDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link OAuth2ClientDataPort} 的 system 侧适配器实现。
 *
 * <p>委托 {@code OpenApiClientService} 完成客户端认证 / 查询，将 {@code system.openapi.domain.model.OpenApiClientDO}
 * 映射为 security 自有 {@link OAuth2ClientView}（scope 经 {@link OpenApiClientRules#parseScopes} 归一），
 * 使 security 侧仅依赖 {@code security.oauth2} 定义的抽象端口。</p>
 */
@Component
@RequiredArgsConstructor
public class OAuth2ClientDataAdapter implements OAuth2ClientDataPort {

    private final OpenApiClientService clientService;

    @Override
    public OAuth2ClientView authenticate(String clientId, String clientSecret) {
        OpenApiClientDO client = clientService.authenticateClient(clientId, clientSecret);
        return toView(client);
    }

    @Override
    public OAuth2ClientView getActive(String clientId) {
        OpenApiClientDO client = clientService.getActiveClient(clientId);
        return toView(client);
    }

    private OAuth2ClientView toView(OpenApiClientDO client) {
        return new OAuth2ClientView(
                client.getClientId(),
                OpenApiClientRules.parseScopes(client.getScopes()),
                OpenApiClientRules.parseRedirectUris(client.getRedirectUris()),
                client.getPkceRequired() != null && client.getPkceRequired() == 1);
    }
}
