package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("OAuth2 令牌签发（RS256）")
class OAuth2TokenProviderTest {

    @Test
    @DisplayName("签发客户端令牌可经 RS256 公钥解码并携带 client_id/scope")
    void issueClientCredentialsToken_rs256() {
        JwtProperties props = new JwtProperties();
        props.setOauth2KeyId("k1");
        props.setOauth2Issuer("iss");
        props.setOauth2Audience("aud");
        RsaKeyProvider keys = new RsaKeyProvider(props);
        OAuth2TokenProvider provider = new OAuth2TokenProvider(keys, props);

        String token = provider.issueClientCredentialsToken("client-x", Set.of("read", "write"));
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
        Jwt jwt = decoder.decode(token);
        assertEquals("client-x", jwt.getSubject());
        assertEquals("client-x", jwt.getClaim("client_id"));
        assertEquals("read write", jwt.getClaim("scope"));
    }

    @Test
    @DisplayName("签发用户令牌携带数值 sub/用户名/权限/版本，可经 RS256 解码")
    void issueUserToken_rs256() {
        JwtProperties props = new JwtProperties();
        props.setOauth2KeyId("k2");
        props.setOauth2Issuer("iss");
        props.setOauth2Audience("aud");
        RsaKeyProvider keys = new RsaKeyProvider(props);
        OAuth2TokenProvider provider = new OAuth2TokenProvider(keys, props);

        String token = provider.issueUserToken("client-x", 42L, "alice", 3,
                Set.of("read"), Set.of("system:user:read", "system:user:write"));
        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
        Jwt jwt = decoder.decode(token);
        assertEquals("42", jwt.getSubject());
        assertEquals("alice", jwt.getClaim("username"));
        assertEquals(Long.valueOf(3), (Long) jwt.getClaim("tokenVersion"));
        assertEquals("system:user:read,system:user:write", jwt.getClaim("authorities"));
    }
}
