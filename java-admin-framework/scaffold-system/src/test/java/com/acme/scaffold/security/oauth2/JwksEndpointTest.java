package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("JWKS 端点")
class JwksEndpointTest {

    @Test
    @DisplayName("暴露 RSA 公钥 JWK")
    void jwks_exposesRsaPublicKey() throws Exception {
        JwtProperties props = new JwtProperties();
        props.setOauth2KeyId("k1");
        JwksEndpoint endpoint = new JwksEndpoint(new RsaKeyProvider(props));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(endpoint).build();
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].kid").value("k1"))
                .andExpect(jsonPath("$.keys[0].n").exists())
                .andExpect(jsonPath("$.keys[0].e").exists());
    }
}
