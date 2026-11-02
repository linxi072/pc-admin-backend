package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.security.jwt.JwtAuthConverter;
import com.acme.scaffold.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OAuth2 资源服务器契约（无 Spring 上下文，纯链路）：
 * 真实 RS256 令牌 → 资源服务器使用的 RS256 解码器 → {@link JwtAuthConverter} → 权威集合。
 *
 * <p>验证「自签发令牌」被资源服务器接收后能正确映射为 {@code SCOPE_<scope>} 权威，
 * 且 OAuth2 客户端令牌（sub=client_id，非数字）不产生用户身份（userId=null）。
 * 这正是 M37「资源服务器 + 自签发 token 端点」的核心契约。</p>
 */
@DisplayName("OAuth2 资源服务器契约：令牌→解码→权威映射")
class OAuth2ResourceServerContractTest {

    private static JwtProperties props() {
        JwtProperties p = new JwtProperties();
        p.setOauth2KeyId("contract-kid");
        p.setOauth2Issuer("contract-iss");
        p.setOauth2Audience("contract-aud");
        return p;
    }

    /** 资源服务器对 OAuth2 令牌使用的 RS256 解码器（与 {@code OAuth2ServerConfig#oauth2JwtDecoder} 等价）。 */
    private static JwtDecoder resourceServerDecoder(RsaKeyProvider keys) {
        return NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
    }

    @Test
    @DisplayName("RS256 客户端令牌经解码+转换后映射为 SCOPE_ 权威且无用户身份")
    void clientToken_mapsScopeToAuthorities_withoutUserIdentity() {
        JwtProperties p = props();
        RsaKeyProvider keys = new RsaKeyProvider(p);
        OAuth2TokenProvider provider = new OAuth2TokenProvider(keys, p);

        String token = provider.issueClientCredentialsToken("client-1", Set.of("read", "write"));
        Jwt jwt = resourceServerDecoder(keys).decode(token);
        Authentication auth = new JwtAuthConverter().convert(jwt);

        assertTrue(auth.isAuthenticated(), "令牌应被接受为已认证主体");
        assertTrue(auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("SCOPE_read")), "应具备 SCOPE_read 权威");
        assertTrue(auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("SCOPE_write")), "应具备 SCOPE_write 权威");

        CurrentPrincipal principal = (CurrentPrincipal) auth.getPrincipal();
        assertNotNull(principal, "principal 应为 CurrentPrincipal");
        assertEquals(null, principal.userId(), "OAuth2 客户端令牌不应携带用户身份");
        assertEquals("client-1", principal.username(), "username 应回落为 clientId");
    }

    @Test
    @DisplayName("仅 read 权限的令牌不含 write 权威（供受 SCOPE_write 保护接口返回 403）")
    void readOnlyToken_lacksWriteAuthority() {
        JwtProperties p = props();
        RsaKeyProvider keys = new RsaKeyProvider(p);
        OAuth2TokenProvider provider = new OAuth2TokenProvider(keys, p);

        String token = provider.issueClientCredentialsToken("client-2", Set.of("read"));
        Jwt jwt = resourceServerDecoder(keys).decode(token);
        Authentication auth = new JwtAuthConverter().convert(jwt);

        boolean hasWrite = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("SCOPE_write"));
        assertFalse(hasWrite, "read-only 令牌不应具备 SCOPE_write 权威");
    }
}
