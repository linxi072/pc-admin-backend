package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.context.CurrentPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2 授权端点")
class OAuth2AuthorizeEndpointTest {

    @Mock
    private OAuth2ClientDataPort clientPort;
    @Mock
    private OAuth2AuthorizationCodeService codeService;

    @InjectMocks
    private OAuth2AuthorizeEndpoint endpoint;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void withPrincipal() {
        CurrentPrincipal p = new CurrentPrincipal(42L, "alice", "Alice", 0L, null, 1,
                Set.of("ADMIN"), Set.of("system:user:read"));
        Authentication auth = mock(Authentication.class);
        // lenient：部分用例在到达 principal 取值前已提前返回（如 scope 校验失败），避免 strict stubbing 误报
        lenient().when(auth.isAuthenticated()).thenReturn(true);
        lenient().when(auth.getPrincipal()).thenReturn(p);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("已认证资源拥有者 + 合法客户端 → 返回授权码")
    void authorize_success() throws Exception {
        when(clientPort.getActive("c1")).thenReturn(new OAuth2ClientView("c1", Set.of("read")));
        withPrincipal();
        when(codeService.issue(eq("c1"), any(), any())).thenReturn("thecode");

        mockMvc.perform(get("/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", "c1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("thecode"));
    }

    @Test
    @DisplayName("资源拥有者未认证 → 401 login_required")
    void authorize_requiresLogin() throws Exception {
        when(clientPort.getActive("c1")).thenReturn(new OAuth2ClientView("c1", Set.of("read")));

        mockMvc.perform(get("/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", "c1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("login_required"));
    }

    @Test
    @DisplayName("请求 scope 超出客户端授权范围 → 400 invalid_scope")
    void authorize_invalidScope() throws Exception {
        when(clientPort.getActive("c1")).thenReturn(new OAuth2ClientView("c1", Set.of("read")));
        withPrincipal();

        mockMvc.perform(get("/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", "c1")
                        .param("scope", "read write admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_scope"));
    }

    @Test
    @DisplayName("不支持的 response_type → 400 unsupported_response_type")
    void authorize_unsupportedResponseType() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .param("response_type", "token")
                        .param("client_id", "c1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_response_type"));
    }
}
