package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OAuth2 令牌吊销校验过滤器单元测试（M43）。
 *
 * <p>仅覆盖「请求期吊销判定」纯逻辑：不依赖 Spring 容器，
 * 直接构造过滤器并以 {@code MockHttpServletRequest/Response} 驱动 {@code doFilterInternal}。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2 令牌吊销校验过滤器")
class OAuth2TokenRevocationFilterTest {

    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private OAuth2TokenRegistry tokenRegistry;

    // 与生产一致：注册 JavaTimeModule，使 Result.timestamp（Instant/LocalDateTime）可序列化。
    // 否则裸 ObjectMapper 会因 Java 8 时间类型不支持而抛 InvalidDefinitionException，
    // 被过滤器 fail-open 捕获后误走放行分支。
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private OAuth2TokenRevocationFilter filter;

    @BeforeEach
    void setup() {
        filter = new OAuth2TokenRevocationFilter(jwtDecoder, tokenRegistry, objectMapper);
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWith(String authorization) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        if (authorization != null) {
            req.addHeader("Authorization", authorization);
        }
        return req;
    }

    private Authentication authenticated(String principalType) {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(true);
        if ("principal".equals(principalType)) {
            CurrentPrincipal principal = mock(CurrentPrincipal.class);
            when(auth.getPrincipal()).thenReturn(principal);
        } else {
            when(auth.getPrincipal()).thenReturn("other");
        }
        return auth;
    }

    private Jwt jwt(String jti, String clientId) {
        Jwt.Builder b = Jwt.withTokenValue("raw").header("alg", "RS256").claim("jti", jti);
        if (clientId != null) {
            b = b.claim("client_id", clientId);
        }
        return b.build();
    }

    @Test
    @DisplayName("已吊销的 OAuth2 令牌 → 401 TOKEN_REVOKED")
    void revoked_oauth2_token_rejected() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticated("principal"));
        when(jwtDecoder.decode("raw")).thenReturn(jwt("jti-1", "client-1"));
        when(tokenRegistry.isRevoked("jti-1")).thenReturn(true);

        MockHttpServletRequest req = requestWith("Bearer raw");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), res.getStatus());
        verify(chain, never()).doFilter(req, res);
        // Result.error 包络，错误码为 AUTH_005
        assertEquals(SecurityErrorCode.TOKEN_REVOKED.code(), objectMapper.readTree(res.getContentAsString()).get("code").asText());
    }

    @Test
    @DisplayName("未吊销的 OAuth2 令牌 → 放行")
    void active_oauth2_token_passes() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticated("principal"));
        when(jwtDecoder.decode("raw")).thenReturn(jwt("jti-2", "client-1"));
        when(tokenRegistry.isRevoked("jti-2")).thenReturn(false);

        MockHttpServletRequest req = requestWith("Bearer raw");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertEquals(HttpStatus.OK.value(), res.getStatus());
    }

    @Test
    @DisplayName("登录令牌（无 client_id 声明）→ 跳过吊销校验直接放行")
    void login_token_without_clientId_passes() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticated("principal"));
        // 登录令牌无 client_id 声明
        when(jwtDecoder.decode("raw")).thenReturn(jwt("jti-3", null));

        MockHttpServletRequest req = requestWith("Bearer raw");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        // 不应查询注册表（避免误伤登录态）
        verify(tokenRegistry, never()).isRevoked(anyString());
    }

    @Test
    @DisplayName("无 Authorization 头 → 直接放行")
    void no_authorization_header_passes() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticated("principal"));

        MockHttpServletRequest req = requestWith(null);
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(jwtDecoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("解码异常 → fail-open 降级放行（不拒绝）")
    void decode_exception_fail_open() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(authenticated("principal"));
        when(jwtDecoder.decode("raw")).thenThrow(new RuntimeException("bad token"));

        MockHttpServletRequest req = requestWith("Bearer raw");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        assertEquals(HttpStatus.OK.value(), res.getStatus());
    }

    @Test
    @DisplayName("未认证请求 → 直接放行（不介入）")
    void unauthenticated_passes() throws Exception {
        Authentication auth = mock(Authentication.class);
        when(auth.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        MockHttpServletRequest req = requestWith("Bearer raw");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(chain).doFilter(req, res);
        verify(jwtDecoder, never()).decode(anyString());
    }

    @Test
    @DisplayName("作用域隔离：仅对携 client_id 的 OAuth2 令牌查询注册表")
    void only_oauth2_token_queried() throws Exception {
        // 同时存在两种令牌请求：OAuth2 令牌查注册表，登录令牌不查
        SecurityContextHolder.getContext().setAuthentication(authenticated("principal"));
        when(jwtDecoder.decode("raw")).thenReturn(jwt("jti-4", "client-1"));
        when(tokenRegistry.isRevoked("jti-4")).thenReturn(false);

        MockHttpServletRequest req = requestWith("Bearer raw");
        MockHttpServletResponse res = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, res, chain);

        verify(tokenRegistry).isRevoked("jti-4");
        verify(chain).doFilter(req, res);
    }
}
