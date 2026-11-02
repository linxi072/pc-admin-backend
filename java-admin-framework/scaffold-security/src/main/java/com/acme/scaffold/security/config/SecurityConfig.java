package com.acme.scaffold.security.config;

import com.acme.scaffold.security.jwt.JwtAuthConverter;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.captcha.CaptchaProperties;
import com.acme.scaffold.security.antireplay.AntiReplayProperties;
import com.acme.scaffold.security.online.OnlineSessionFilter;
import com.acme.scaffold.security.oauth2.OAuth2TokenRevocationFilter;
import com.acme.scaffold.security.oauth2.OAuth2TokenRegistry;
import com.acme.scaffold.security.token.TokenVersionVerifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
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
@EnableConfigurationProperties({JwtProperties.class, CaptchaProperties.class, AntiReplayProperties.class, SwaggerProperties.class})
public class SecurityConfig {

    private final SwaggerProperties swaggerProperties;

    public SecurityConfig(SwaggerProperties swaggerProperties) {
        this.swaggerProperties = swaggerProperties;
    }

    /** 始终公开的基础接口（鉴权、健康探针、错误页）。 */
    private static final RequestMatcher BASE_PUBLIC_API = new OrRequestMatcher(
            new AntPathRequestMatcher("/api/auth/login", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/auth/refresh", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/auth/change-password", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/auth/forgot-password", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/api/auth/captcha", HttpMethod.GET.name()),
            // 健康探针（k8s 风格）：liveness/readiness 不应要求鉴权
            new AntPathRequestMatcher("/livez", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/readyz", HttpMethod.GET.name()),
            // Alertmanager webhook 接收端：外部告警系统无法出示 JWT，
            // 鉴权由控制器内 enabled 开关 + X-Alert-Token 承担（收件箱查询仍须登录）
            new AntPathRequestMatcher("/api/monitoring/alerts/webhook", HttpMethod.POST.name()),
            // 通知退订端点（M15）：邮件/短信正文中的签名链接，外部浏览器点击时无法出示 JWT，
            // 鉴权由令牌 HMAC-SHA256 签名 + 过期时间承担；控制器内另加 IP 级限流防爆破。
            // 仅放行 GET——退订是链接点击语义，写侧无入参用户标识，不开放 POST 批量面
            new AntPathRequestMatcher("/api/notification/unsubscribe", HttpMethod.GET.name()),
            // 前后端联通探针端点：匿名可访问是联调契约（前端 http.js 探测 / 探针脚本
            // 均无 token）；ping 零数据库依赖，slow 的 delayMs 已由 resolveDelayMs
            // 收敛到 [0,10000] 防线程耗尽型滥用
            new AntPathRequestMatcher("/api/connectivity/ping", HttpMethod.GET.name()),
            new AntPathRequestMatcher("/api/connectivity/slow", HttpMethod.GET.name()),
            // OAuth2 授权服务器端点（协议级开放平台）：令牌签发与 JWKS 公钥暴露，匿名可访问；
            // 真实安全性由端点内 client 认证（client_id/client_secret 校验）承担
            new AntPathRequestMatcher("/oauth2/token", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/oauth2/jwks", HttpMethod.GET.name()),
            // 令牌自省端点（RFC 7662）：匿名可到达，由端点内部校验调用方凭证（Bearer 或 client Basic）
            new AntPathRequestMatcher("/oauth2/introspect", HttpMethod.POST.name()),
            new AntPathRequestMatcher("/error"));

    /** Swagger / OpenAPI 文档端点（是否公开由 {@code app.swagger.public-access} 决定）。 */
    private static final RequestMatcher SWAGGER_API = new OrRequestMatcher(
            new AntPathRequestMatcher("/v3/api-docs/**"),
            new AntPathRequestMatcher("/swagger-ui/**"),
            new AntPathRequestMatcher("/swagger-ui.html"));

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
                                          org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder,
                                          TokenVersionVerifier tokenVersionVerifier,
                                          OnlineSessionFilter onlineSessionFilter,
                                          OAuth2TokenRevocationFilter oauth2TokenRevocationFilter) throws Exception {
        // 文档端点是否公开由 app.swagger.public-access 决定（默认需鉴权）
        RequestMatcher publicApi = swaggerProperties.isPublicAccess()
                ? new OrRequestMatcher(BASE_PUBLIC_API, SWAGGER_API)
                : BASE_PUBLIC_API;
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(org.springframework.security.config.Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(publicApi).permitAll()
                        // WebSocket 握手走 HTTP 升级，token 在 query 参数（浏览器 WS 不支持自定义 header），
                        // 此处放行后由 RealtimeHandshakeInterceptor 手动校验 JWT，失败则拒绝握手
                        .requestMatchers(new AntPathRequestMatcher("/ws/**")).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthConverter))
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .accessDeniedHandler(new BearerTokenAccessDeniedHandler()))
                // Token 版本校验必须排在 Bearer 认证之后：先有主体（含 userId 与 tokenVersion 声明）才谈得上比对
                .addFilterAfter(tokenVersionVerifier,
                        org.springframework.security.oauth2.server.resource.web.authentication
                                .BearerTokenAuthenticationFilter.class)
                // 在线会话校验 + 心跳（V27）：强制下线/登出/限制后设备侧下一个请求即 401
                .addFilterAfter(onlineSessionFilter, TokenVersionVerifier.class)
                // OAuth2 令牌吊销校验（M43）：仅对携 client_id 声明的 OAuth2 令牌生效，已吊销即 401
                .addFilterAfter(oauth2TokenRevocationFilter, OnlineSessionFilter.class);
        return http.build();
    }

    /**
     * 关闭 {@link TokenVersionVerifier} 的 Servlet 容器自动注册。
     *
     * <p>原因：它是 {@code Filter} 类型的 Bean，Spring Boot 会把它同时挂到容器级过滤链上，
     * 导致请求在进入 Security 链之前先跑一遍——那时根本没有认证主体，纯属空转。
     * 真正需要的注册点只有一处：Security 链内 Bearer 认证之后（见 {@code addFilterAfter}）。
     */
    @Bean
    public FilterRegistrationBean<TokenVersionVerifier> tokenVersionVerifierRegistration(
            TokenVersionVerifier verifier) {
        FilterRegistrationBean<TokenVersionVerifier> registration = new FilterRegistrationBean<>(verifier);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * 关闭 {@link OnlineSessionFilter} 的 Servlet 容器自动注册（原因同上）：
     * 只在 Security 链内 Token 版本校验之后生效，保证认证主体已就绪。
     */
    @Bean
    public FilterRegistrationBean<OnlineSessionFilter> onlineSessionFilterRegistration(
            OnlineSessionFilter filter) {
        FilterRegistrationBean<OnlineSessionFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * OAuth2 令牌吊销校验过滤器（M43）。声明为 Bean 仅由 {@link #apiSecurity} 在 Security 链内串接，
     * 并经 {@link #oauth2TokenRevocationFilterRegistration} 关闭 Servlet 容器自动注册，
     * 避免在排除本配置的 @WebMvcTest 中被误当作全局 Servlet 过滤器重复生效。
     */
    @Bean
    public OAuth2TokenRevocationFilter oauth2TokenRevocationFilter(
            org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder,
            OAuth2TokenRegistry tokenRegistry,
            ObjectMapper objectMapper) {
        return new OAuth2TokenRevocationFilter(jwtDecoder, tokenRegistry, objectMapper);
    }

    /**
     * 关闭 {@link OAuth2TokenRevocationFilter} 的 Servlet 容器自动注册（原因同上）：
     * 只在 Security 链内在线会话校验之后生效，保证认证主体与 OAuth2 令牌声明已就绪。
     */
    @Bean
    public FilterRegistrationBean<OAuth2TokenRevocationFilter> oauth2TokenRevocationFilterRegistration(
            OAuth2TokenRevocationFilter filter) {
        FilterRegistrationBean<OAuth2TokenRevocationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 默认 BCrypt，兼容 {bcrypt}、{noop} 等前缀，便于迁移历史口令
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
