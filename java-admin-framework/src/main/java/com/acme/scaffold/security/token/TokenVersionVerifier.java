package com.acme.scaffold.security.token;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.security.context.CurrentPrincipal;
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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Token 版本校验过滤器：位于 Bearer 认证之后，比对凭证中的 {@code tokenVersion} 与库中当前版本。
 *
 * <p><b>为什么放在这里而不是 {@code JwtAuthConverter}</b>：Converter 只负责「解出主体」，
 * 不持有数据源；且它无法写响应。版本校验需要查库并可能中断请求，天然属于过滤器职责。
 *
 * <p><b>fail-open 立场</b>：版本校验依赖 DB。若 DB 抖动导致读不到版本，
 * 一律放行而非 401——基础设施故障不应该表现为「全站被登出」。
 * 真正的失效（版本确实落后）才会拒绝。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenVersionVerifier extends OncePerRequestFilter {

    private final TokenVersionService tokenVersionService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CurrentPrincipal principal
                && principal.userId() != null) {
            try {
                if (!tokenVersionService.isValid(principal.userId(), principal.tokenVersion())) {
                    log.info("Token 版本已失效，拒绝请求 userId={} claimVersion={} path={}",
                            principal.userId(), principal.tokenVersion(), request.getRequestURI());
                    SecurityContextHolder.clearContext();
                    writeUnauthorized(response);
                    return;
                }
            } catch (Exception e) {
                log.warn("Token 版本校验异常，降级放行 userId={}", principal.userId(), e);
            }
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
