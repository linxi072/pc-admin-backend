package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * OAuth2 令牌签发 jti 重载测试（M43）。
 *
 * <p>验证新增的「jti 由调用方透传」重载会把 jti 同时写入 JWT 的 {@code jti} 声明与 {@code id}，
 * 保证与注册表落库的 jti 严格一致（吊销校验依此匹配）。</p>
 */
@DisplayName("OAuth2 令牌签发（jti 重载）")
class OAuth2TokenProviderJtiTest {

    /** 构造同一把 RSA 密钥的签发器与解码器（密钥每次构造随机生成，必须共享实例）。 */
    private static class Fixture {
        final RsaKeyProvider keys;
        final OAuth2TokenProvider provider;
        final JwtDecoder decoder;

        Fixture() {
            JwtProperties props = new JwtProperties();
            props.setOauth2KeyId("kj");
            props.setOauth2Issuer("iss");
            props.setOauth2Audience("aud");
            this.keys = new RsaKeyProvider(props);
            this.provider = new OAuth2TokenProvider(keys, props);
            this.decoder = NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
        }
    }

    @Test
    @DisplayName("客户端令牌 jti 重载：JWT id 与传入 jti 一致")
    void client_token_jti_propagated() {
        Fixture f = new Fixture();
        String jti = "jti-client-abc";
        String token = f.provider.issueClientCredentialsToken("client-1", Set.of("read"), jti);
        Jwt jwt = f.decoder.decode(token);
        assertEquals(jti, jwt.getId());
        assertEquals(jti, jwt.getClaim("jti"));
        assertEquals("client-1", jwt.getClaim("client_id"));
    }

    @Test
    @DisplayName("用户令牌 jti 重载：JWT id 与传入 jti 一致并携带用户声明")
    void user_token_jti_propagated() {
        Fixture f = new Fixture();
        String jti = "jti-user-xyz";
        String token = f.provider.issueUserToken("client-1", 42L, "alice", 3,
                Set.of("read"), Set.of("system:user:read"), jti);
        Jwt jwt = f.decoder.decode(token);
        assertEquals(jti, jwt.getId());
        assertEquals("42", jwt.getSubject());
        assertEquals("alice", jwt.getClaim("username"));
    }
}
