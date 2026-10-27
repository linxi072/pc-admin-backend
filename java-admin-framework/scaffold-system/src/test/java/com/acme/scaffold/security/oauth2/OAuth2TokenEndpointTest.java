package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.jwt.JwtProperties;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2 令牌端点")
class OAuth2TokenEndpointTest {

    @Mock
    private OpenApiClientService clientService;
    @Mock
    private OAuth2TokenProvider tokenProvider;
    @Mock
    private JwtProperties properties;

    @InjectMocks
    private OAuth2TokenEndpoint endpoint;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint).build();
    }

    @Test
    @DisplayName("client_credentials 成功返回 access_token")
    void token_success() throws Exception {
        OpenApiClientDO client = new OpenApiClientDO();
        client.setClientId("client-1");
        client.setScopes("read write");
        when(clientService.authenticateClient("client-1", "secret")).thenReturn(client);
        when(tokenProvider.issueClientCredentialsToken(eq("client-1"), eq(Set.of("read", "write"))))
                .thenReturn("tok-abc");
        when(properties.getOauth2TokenTtl()).thenReturn(Duration.ofSeconds(1800));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "client-1")
                        .param("client_secret", "secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("tok-abc"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(1800))
                .andExpect(jsonPath("$.scope").value("read write"));
    }

    @Test
    @DisplayName("Basic 认证头被识别")
    void token_basicAuth() throws Exception {
        OpenApiClientDO client = new OpenApiClientDO();
        client.setClientId("client-2");
        client.setScopes("read");
        when(clientService.authenticateClient("client-2", "pw")).thenReturn(client);
        when(tokenProvider.issueClientCredentialsToken(eq("client-2"), eq(Set.of("read"))))
                .thenReturn("tok-2");
        when(properties.getOauth2TokenTtl()).thenReturn(Duration.ofSeconds(600));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .header("Authorization",
                                "Basic " + Base64.getEncoder().encodeToString("client-2:pw".getBytes()))
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("tok-2"));
    }

    @Test
    @DisplayName("grant_type 不支持 → 400 invalid_request")
    void token_invalidRequest() throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", "id")
                        .param("client_secret", "pw"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    @DisplayName("客户端认证失败 → 401 invalid_client")
    void token_invalidClient() throws Exception {
        when(clientService.authenticateClient(anyString(), anyString()))
                .thenThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端密钥错误"));
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "id")
                        .param("client_secret", "bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }
}
