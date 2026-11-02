package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.security.jwt.JwtProperties;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("OAuth2 用户同意端点")
class OAuth2ConsentEndpointTest {

    private RsaKeyProvider keys;
    private OAuth2AuthorizationCodeService codeService;
    private OAuth2ConsentService consentService;
    private OAuth2ConsentEndpoint endpoint;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        keys = new RsaKeyProvider(new JwtProperties());
        codeService = new OAuth2AuthorizationCodeService(keys);
        consentService = mock(OAuth2ConsentService.class);
        endpoint = new OAuth2ConsentEndpoint(codeService, consentService, keys);
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void withPrincipal(Long userId) {
        CurrentPrincipal p = new CurrentPrincipal(userId, "alice", "Alice", 0L, null, 1,
                Set.of("ADMIN"), Set.of("system:user:read"));
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        when(auth.getPrincipal()).thenReturn(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String signConsentToken(Long userId, String clientId, String scope, String redirectUri, String state) {
        RSAPrivateKey key = keys.getPrivateKey();
        Date now = new Date();
        Date exp = new Date(now.getTime() + 5 * 60 * 1000L);
        return Jwts.builder()
                .setHeaderParam("kid", keys.getKeyId())
                .subject(userId.toString())
                .claim("client_id", clientId)
                .claim("scope", scope)
                .claim("redirect_uri", redirectUri)
                .claim("state", state == null ? "" : state)
                .claim("code_challenge", "")
                .claim("code_challenge_method", "")
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(exp)
                .signWith(key, Jwts.SIG.RS256)
                .compact();
    }

    @Test
    @DisplayName("allow → 记录同意并签发授权码")
    void consent_allow() throws Exception {
        withPrincipal(42L);
        String token = signConsentToken(42L, "c1", "read", "https://a.example/cb", "st");

        mockMvc.perform(post("/oauth2/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent_token\":\"" + token + "\",\"decision\":\"allow\",\"remember\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.state").value("st"));
        verify(consentService).record(eq(42L), eq("c1"), eq(Set.of("read")), eq(true));
    }

    @Test
    @DisplayName("deny → access_denied")
    void consent_deny() throws Exception {
        withPrincipal(42L);
        String token = signConsentToken(42L, "c1", "read", "https://a.example/cb", null);

        mockMvc.perform(post("/oauth2/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent_token\":\"" + token + "\",\"decision\":\"deny\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("access_denied"));
    }

    @Test
    @DisplayName("无效 consent_token → invalid_grant")
    void consent_invalidToken() throws Exception {
        withPrincipal(42L);
        mockMvc.perform(post("/oauth2/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent_token\":\"garbage\",\"decision\":\"allow\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("consent_token 用户与当前用户不一致 → invalid_grant")
    void consent_userMismatch() throws Exception {
        withPrincipal(42L);
        String token = signConsentToken(999L, "c1", "read", "https://a.example/cb", null);

        mockMvc.perform(post("/oauth2/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent_token\":\"" + token + "\",\"decision\":\"allow\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    @Test
    @DisplayName("未认证 → login_required")
    void consent_unauthenticated() throws Exception {
        String token = signConsentToken(42L, "c1", "read", "https://a.example/cb", null);
        mockMvc.perform(post("/oauth2/consent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent_token\":\"" + token + "\",\"decision\":\"allow\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("login_required"));
    }
}
