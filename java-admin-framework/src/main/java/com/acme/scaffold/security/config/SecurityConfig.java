package com.acme.scaffold.security.config;

import com.acme.scaffold.security.jwt.JwtAuthConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * 安全配置：基于 Bearer JWT 的无状态资源服务器。
 * <ul>
 *   <li>Actuator 端点单独隔离，仅 health/prometheus 公开，其余需 platform:actuator:read；</li>
 *   <li>API 链为无状态、关闭 CSRF（令牌认证不依赖 Cookie），全部请求需认证；</li>
 *   <li>接口级权限通过 {@code @PreAuthorize("hasAuthority('system:user:read')")} 控制。</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final RequestMatcher PUBLIC_API = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/auth/login", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/auth/refresh", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/v3/api-docs/**"),
            new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/swagger-ui.html"),
            new AntPathRequestMatcher("/error"));

    @Bean
    @Order(1)
    public SecurityFilterChain actuatorSecurity(HttpSecurity http) throws Exception {
        http.securityMatcher(org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.toAnyEndpoint())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.to("health", "prometheus"))
                        .permitAll()
                        .anyRequest().hasAuthority("platform:actuator:read"))
                .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest.toAnyEndpoint()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiSecurity(HttpSecurity http, JwtAuthConverter jwtAuthConverter,
                                          org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_API).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthConverter))
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 默认 BCrypt，兼容 {bcrypt}、{noop} 等前缀，便于迁移历史口令
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
