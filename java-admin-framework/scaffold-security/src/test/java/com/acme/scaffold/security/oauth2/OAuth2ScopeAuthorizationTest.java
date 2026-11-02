package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.security.config.SecurityConfig;
import com.acme.scaffold.security.jwt.JwtAuthConverter;
import com.acme.scaffold.security.jwt.JwtProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OAuth2 资源服务器端到端授权契约（自包含 SecurityFilterChain，等价于生产
 * {@code SecurityConfig#apiSecurity} 的 oauth2ResourceServer 接线）：
 * 用真实 {@link OAuth2TokenProvider} 签发的 RS256 令牌，经真实 RS256 解码器与
 * {@link JwtAuthConverter}（scope→SCOPE_ 权威映射）后，命中 {@code @PreAuthorize} 端点。
 *
 * <p>覆盖三种结果：携带 read 权限 → 200；缺失 read 权限 → 403；无 Bearer → 401。</p>
 */
@WebMvcTest(excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = SecurityConfig.class))
@DisplayName("OAuth2 资源服务器端到端授权（令牌→解码→@PreAuthorize）")
class OAuth2ScopeAuthorizationTest {

    @Configuration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SampleApi sampleApi() {
            return new SampleApi();
        }

        @Bean
        JwtProperties jwtProperties() {
            JwtProperties p = new JwtProperties();
            p.setOauth2KeyId("e2e-kid");
            p.setOauth2Issuer("e2e-iss");
            p.setOauth2Audience("e2e-aud");
            p.setOauth2TokenTtl(Duration.ofHours(1));
            return p;
        }

        @Bean
        RsaKeyProvider rsaKeyProvider(JwtProperties p) {
            return new RsaKeyProvider(p);
        }

        /** 资源服务器对 OAuth2 令牌使用的 RS256 解码器（与生产一致）。 */
        @Bean
        JwtDecoder oauth2JwtDecoder(RsaKeyProvider keys) {
            return NimbusJwtDecoder.withPublicKey(keys.getPublicKey()).build();
        }

        @Bean
        JwtAuthConverter jwtAuthConverter() {
            return new JwtAuthConverter();
        }

        @Bean
        SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder oauth2JwtDecoder,
                                        JwtAuthConverter jwtAuthConverter) throws Exception {
            http.csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .oauth2ResourceServer(oauth2 -> oauth2
                            .jwt(jwt -> jwt.decoder(oauth2JwtDecoder)
                                    .jwtAuthenticationConverter(jwtAuthConverter))
                            .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                            .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
            return http.build();
        }
    }

    @RestController
    static class SampleApi {
        @GetMapping("/api/sample/scoped")
        @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('SCOPE_read')")
        public Map<String, Object> scoped() {
            return Map.of("ok", true);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;
    @org.springframework.beans.factory.annotation.Autowired
    private RsaKeyProvider keys;
    @org.springframework.beans.factory.annotation.Autowired
    private JwtProperties props;

    private String tokenWith(Set<String> scopes) {
        return new OAuth2TokenProvider(keys, props).issueClientCredentialsToken("client", scopes);
    }

    @Test
    @DisplayName("携带 SCOPE_read 的令牌访问受保护接口 → 200")
    void token_with_read_scope_gets_200() throws Exception {
        String token = tokenWith(Set.of("read"));
        mockMvc.perform(get("/api/sample/scoped").header("Authorization", "Bearer " + token))
                .andExpect(status().is(HttpStatus.OK.value()));
    }

    @Test
    @DisplayName("令牌仅含 write、缺 read → 403")
    void token_without_read_scope_gets_403() throws Exception {
        String token = tokenWith(Set.of("write"));
        mockMvc.perform(get("/api/sample/scoped").header("Authorization", "Bearer " + token))
                .andExpect(status().is(HttpStatus.FORBIDDEN.value()));
    }

    @Test
    @DisplayName("无 Bearer 令牌 → 401")
    void no_token_gets_401() throws Exception {
        mockMvc.perform(get("/api/sample/scoped"))
                .andExpect(status().is(HttpStatus.UNAUTHORIZED.value()));
    }
}
