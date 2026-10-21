package com.acme.scaffold.security.online;

import com.acme.scaffold.common.api.Result;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.system.service.OnlineUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在线会话过滤器（V27）：位于 Token 版本校验之后，承担两个职责——
 * <ol>
 *   <li><b>会话状态校验</b>：JWT 合法但会话已被强制下线/登出/限制（sys_online_session 置 OFFLINE）
 *       的请求直接 401。弥补 access token 最长 30 分钟有效期内「踢而不掉」的空窗，
 *       使强制下线操作对设备侧<b>下一个请求即生效</b>；</li>
 *   <li><b>心跳保活</b>：按 {@link HeartbeatThrottle} 节流更新 last_active_at，
 *       支撑列表的「最近活跃时间」与会话状态判定。</li>
 * </ol>
 *
 * <p><b>fail-open 立场</b>（与 {@code TokenVersionVerifier} 一致）：
 * <ul>
 *   <li>DB 异常 → 放行，基础设施故障不应表现为全站登出；</li>
 *   <li>无会话行（改造前签发的历史令牌）→ 放行，避免升级瞬间全量用户被误踢；</li>
 *   <li>无 X-Device-Id 头 → 无法定位会话，跳过校验与心跳（非浏览器客户端场景）。</li>
 * </ul>
 */
@Slf4j
@Component
@Order(2)
public class OnlineSessionFilter extends OncePerRequestFilter {

    private final OnlineUserService onlineUserService;
    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;
    private final HeartbeatThrottle throttle;

    public OnlineSessionFilter(OnlineUserService onlineUserService, JwtProperties jwtProperties,
                               ObjectMapper objectMapper) {
        this.onlineUserService = onlineUserService;
        this.jwtProperties = jwtProperties;
        this.objectMapper = objectMapper;
        this.throttle = new HeartbeatThrottle(
                jwtProperties.getOnlineSessionHeartbeatInterval().toMillis());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CurrentPrincipal principal)
                || principal.userId() == null) {
            filterChain.doFilter(request, response);
            return;
        }
        String deviceId = request.getHeader("X-Device-Id");
        if (deviceId == null || deviceId.isBlank()) {
            // 无设备标识：无法定位会话（如服务端脚本调用），跳过校验与心跳
            filterChain.doFilter(request, response);
            return;
        }
        Long userId = principal.userId();

        try {
            Boolean state = onlineUserService.sessionState(userId, deviceId);
            if (Boolean.FALSE.equals(state)) {
                log.info("会话已失效（强制下线/登出/限制），拒绝请求 userId={} path={}",
                        userId, request.getRequestURI());
                writeUnauthorized(response);
                return;
            }
        } catch (Exception e) {
            log.warn("在线会话校验异常，降级放行 userId={}", userId, e);
        }

        // 心跳节流：到点才写库，失败仅记日志不阻断请求
        if (throttle.tryAcquire(userId + "|" + deviceId, System.currentTimeMillis())) {
            try {
                onlineUserService.touch(userId, deviceId);
            } catch (Exception e) {
                log.debug("在线会话心跳更新失败 userId={} err={}", userId, e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                Result.error(SecurityErrorCode.TOKEN_REVOKED.code(), "会话已失效，请重新登录"));
    }
}
