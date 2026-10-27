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
    @DisplayName("签发令牌可经 RS256 公钥解码并携带 client_id/scope")
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
}
