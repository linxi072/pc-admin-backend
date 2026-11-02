package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * OAuth2 令牌吊销校验过滤器（M43 令牌治理）。
 *
 * <p>位于 Bearer 认证之后，对携带 {@code client_id} 声明的 OAuth2 令牌（即 RS256 签发的
 * client_credentials / password / authorization_code 令牌）校验其 jti 是否已被吊销
 * （{@link OAuth2TokenRegistry#isRevoked}）。已吊销则拒绝请求（401 TOKEN_REVOKED）。
 *
 * <p><b>作用域</b>：仅作用于 OAuth2 令牌。登录令牌（HS256，无 {@code client_id} 声明）不在此过滤器管辖，
 * 其生命周期由 Token 版本校验 / 在线会话校验负责，避免误伤；且注册表仅登记 OAuth2 令牌，
 * 对登录令牌的 jti 查询必为空，若误判会错误拒绝登录态，故以 {@code client_id} 声明为闸门。</p>
 *
 * <p><b>fail-open</b>：注册表/解码异常一律降级放行（告警），与 {@code TokenVersionVerifier} 立场一致——
 * 基础设施故障不应表现为「全站被登出」。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class OAuth2TokenRevocationFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final OAuth2TokenRegistry tokenRegistry;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CurrentPrincipal)) {
            filterChain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = authorization.substring(7).trim();
        try {
            Jwt jwt = jwtDecoder.decode(token);
            // 仅 OAuth2 令牌携带 client_id 声明；登录令牌无此声明，跳过吊销校验
            String clientId = jwt.getClaimAsString("client_id");
            if (clientId == null || clientId.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }
            // 优先按 jti 声明匹配注册表（签发时经 .id(jti) 写入，对应 JWT 的 jti 声明）。
            // 直接读声明而非 Jwt.getId()，避免测试/不同解码实现下 getId() 语义差异。
            String jti = jwt.getClaimAsString("jti");
            if (jti != null && tokenRegistry.isRevoked(jti)) {
                log.info("OAuth2 令牌已吊销，拒绝请求 jti={} path={}", jti, request.getRequestURI());
                writeUnauthorized(response);
                return;
            }
        } catch (Exception e) {
            log.warn("OAuth2 吊销校验异常，降级放行 path={}", request.getRequestURI(), e);
        }
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                Result.error(SecurityErrorCode.TOKEN_REVOKED.code(), SecurityErrorCode.TOKEN_REVOKED.message()));
    }
}
