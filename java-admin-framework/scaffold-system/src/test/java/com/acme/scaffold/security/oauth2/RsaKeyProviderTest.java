package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("OAuth2 RSA 密钥提供方")
class RsaKeyProviderTest {

    @Test
    @DisplayName("未配置私钥时生成临时密钥，kid 取默认值")
    void ephemeralKey_generated() {
        JwtProperties props = new JwtProperties();
        RsaKeyProvider provider = new RsaKeyProvider(props);
        assertNotNull(provider.getPrivateKey());
        assertNotNull(provider.getPublicKey());
        assertEquals("oauth2-rs256", provider.getKeyId());
    }

    @Test
    @DisplayName("配置固定私钥时派生公钥与原始公钥一致")
    void fixedKey_derivesMatchingPublic() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        KeyPair kp = gen.generateKeyPair();
        String b64 = Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded());

        JwtProperties props = new JwtProperties();
        props.setOauth2PrivateKey(b64);
        props.setOauth2KeyId("fixed-1");
        RsaKeyProvider provider = new RsaKeyProvider(props);

        assertEquals("fixed-1", provider.getKeyId());
        assertEquals(kp.getPublic(), provider.getPublicKey());
    }
}
