package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.system.openapi.OpenApiClientService;
import com.acme.scaffold.system.openapi.domain.OpenApiClientDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2 令牌自省端点（RFC 7662）")
class OAuth2IntrospectionEndpointTest {

    @Mock
    private JwtDecoder decoder;
    @Mock
    private OpenApiClientService clientService;

    @InjectMocks
    private OAuth2IntrospectionEndpoint endpoint;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint).build();
    }

    private Jwt activeJwt() {
        return Jwt.withTokenValue("tv").header("alg", "RS256")
                .subject("42").claim("scope", "read")
                .claim("client_id", "c1").claim("username", "alice")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    @DisplayName("未认证调用方 → 401 invalid_client")
    void introspect_requiresCaller() throws Exception {
        mockMvc.perform(post("/oauth2/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("token", "tok"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    @DisplayName("Bearer 调用方 + 活跃令牌 → active=true 并附带声明")
    void introspect_active_bearer() throws Exception {
        when(decoder.decode("tok")).thenReturn(activeJwt());
        mockMvc.perform(post("/oauth2/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Authorization", "Bearer tok")
                        .param("token", "tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sub").value("42"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.client_id").value("c1"));
    }

    @Test
    @DisplayName("Basic 客户端调用方 + 活跃令牌 → active=true")
    void introspect_active_basic() throws Exception {
        when(clientService.authenticateClient("c1", "s")).thenReturn(new OpenApiClientDO());
        when(decoder.decode("tok")).thenReturn(activeJwt());
        mockMvc.perform(post("/oauth2/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Authorization",
                                "Basic " + Base64.getEncoder().encodeToString("c1:s".getBytes()))
                        .param("token", "tok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("无效令牌 → active=false（RFC 要求 200）")
    void introspect_inactive() throws Exception {
        when(decoder.decode("caller")).thenReturn(activeJwt());
        when(decoder.decode("bad")).thenThrow(new JwtException("expired"));
        mockMvc.perform(post("/oauth2/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Authorization", "Bearer caller")
                        .param("token", "bad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @DisplayName("缺 token 参数 → 400 invalid_request")
    void introspect_missingToken() throws Exception {
        mockMvc.perform(post("/oauth2/introspect")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Authorization", "Bearer tok"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }
}
