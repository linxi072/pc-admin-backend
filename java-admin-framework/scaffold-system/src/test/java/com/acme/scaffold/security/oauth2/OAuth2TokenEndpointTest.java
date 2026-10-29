package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.error.SecurityErrorCode;
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

import static org.mockito.ArgumentMatchers.any;
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
    @Mock
    private OAuth2UserAuthenticator userAuthenticator;
    @Mock
    private OAuth2AuthorizationCodeService codeService;

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
    @DisplayName("grant_type=password 但缺用户名密码 → 400 invalid_request")
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
        when(clientService.authenticateClient(any(), any()))
                .thenThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED, "客户端密钥错误"));
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "id")
                        .param("client_secret", "bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("invalid_client"));
    }

    @Test
    @DisplayName("password 授权成功返回代表用户的令牌")
    void password_grant_success() throws Exception {
        OpenApiClientDO client = new OpenApiClientDO();
        client.setClientId("c1");
        client.setScopes("read");
        when(clientService.authenticateClient("c1", "s")).thenReturn(client);
        OAuth2UserAuthenticator.AuthenticatedUser u = new OAuth2UserAuthenticator.AuthenticatedUser(
                42L, "alice", "Alice", 0L, 1, Set.of("system:user:read"), Set.of("ADMIN"));
        when(userAuthenticator.authenticate("alice", "pw")).thenReturn(u);
        when(tokenProvider.issueUserToken(eq("c1"), eq(42L), eq("alice"), eq(1), any(), eq(Set.of("system:user:read"))))
                .thenReturn("tok-u");
        when(properties.getOauth2TokenTtl()).thenReturn(Duration.ofSeconds(1800));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", "c1")
                        .param("client_secret", "s")
                        .param("username", "alice")
                        .param("password", "pw"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("tok-u"));
    }

    @Test
    @DisplayName("password 凭证错误 → 400 invalid_grant")
    void password_grant_bad_credentials() throws Exception {
        OpenApiClientDO client = new OpenApiClientDO();
        client.setClientId("c1");
        client.setScopes("read");
        when(clientService.authenticateClient("c1", "s")).thenReturn(client);
        when(userAuthenticator.authenticate("alice", "bad"))
                .thenThrow(new BusinessException(CommonErrorCode.UNAUTHORIZED, "用户名或密码错误"));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "password")
                        .param("client_id", "c1")
                        .param("client_secret", "s")
                        .param("username", "alice")
                        .param("password", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("authorization_code 兑换成功返回代表用户的令牌")
    void authorization_code_grant_success() throws Exception {
        OpenApiClientDO client = new OpenApiClientDO();
        client.setClientId("c1");
        client.setScopes("read");
        when(clientService.authenticateClient("c1", "s")).thenReturn(client);
        OAuth2AuthorizationCodeService.CodeContext ctx = new OAuth2AuthorizationCodeService.CodeContext(
                42L, "alice", 1, Set.of("system:user:read"), Set.of("ADMIN"), "c1", "read", null);
        when(codeService.consume("thecode", "c1", null)).thenReturn(ctx);
        when(tokenProvider.issueUserToken(eq("c1"), eq(42L), eq("alice"), eq(1), any(), eq(Set.of("system:user:read"))))
                .thenReturn("tok-ac");
        when(properties.getOauth2TokenTtl()).thenReturn(Duration.ofSeconds(1800));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "c1")
                        .param("client_secret", "s")
                        .param("code", "thecode"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("tok-ac"));
    }

    @Test
    @DisplayName("authorization_code 无效 → 400 invalid_grant")
    void authorization_code_grant_invalid() throws Exception {
        OpenApiClientDO client = new OpenApiClientDO();
        client.setClientId("c1");
        client.setScopes("read");
        when(clientService.authenticateClient("c1", "s")).thenReturn(client);
        when(codeService.consume("bad", "c1", null))
                .thenThrow(new BusinessException(SecurityErrorCode.OAUTH2_INVALID_GRANT, "授权码无效"));

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "c1")
                        .param("client_secret", "s")
                        .param("code", "bad"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }
}
