package com.acme.scaffold.security.application;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.config.JwtProperties;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.security.dto.LoginCommand;
import com.acme.scaffold.security.dto.RefreshCommand;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.jwt.JwtProvider;
import com.acme.scaffold.security.permission.PermissionService;
import com.acme.scaffold.security.token.RefreshTokenService;
import com.acme.scaffold.security.vo.TokenView;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 认证应用服务：登录、刷新、登出，负责密码校验、账号锁定、令牌签发与轮换。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final DSLContext dsl;
    private final PermissionService permissionService;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final SecurityContextFacade securityContextFacade;

    @Transactional
    public TokenView login(LoginCommand command, String ip, String userAgent, String deviceId) {
        SysUserDO user = JooqWriters.fetchOne(dsl, JooqTables.SYS_USER, SysUserDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("username", String.class).eq(command.username())));
        if (user == null) {
            throw new BusinessException(SecurityErrorCode.BAD_CREDENTIALS, "用户名或密码错误");
        }
        if (isLocked(user)) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_LOCKED, "账号已锁定，请稍后再试");
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_DISABLED, "账号已禁用");
        }
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            handleLoginFailure(user);
            throw new BusinessException(SecurityErrorCode.BAD_CREDENTIALS, "用户名或密码错误");
        }
        resetLoginFailure(user);

        CurrentPrincipal principal = buildPrincipal(user);
        String accessToken = jwtProvider.generateAccessToken(principal);
        String refreshToken = refreshTokenService.issue(user.getId(), "web", deviceId, userAgent, ip);
        return new TokenView(accessToken, refreshToken, jwtProperties.getAccessTokenTtl().getSeconds());
    }

    @Transactional
    public TokenView refresh(RefreshCommand command) {
        Long userId = refreshTokenService.verify(command.refreshToken());
        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, userId);
        if (user == null || "DISABLED".equals(user.getStatus())) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_DISABLED, "账号已禁用");
        }
        CurrentPrincipal principal = buildPrincipal(user);
        String accessToken = jwtProvider.generateAccessToken(principal);
        String newRefresh = refreshTokenService.rotate(command.refreshToken());
        return new TokenView(accessToken, newRefresh, jwtProperties.getAccessTokenTtl().getSeconds());
    }

    @Transactional
    public void logout() {
        Long userId = securityContextFacade.requireCurrentPrincipal().userId();
        refreshTokenService.revokeByUser(userId);
    }

    private CurrentPrincipal buildPrincipal(SysUserDO user) {
        Set<String> permissions = permissionService.getPermissions(user.getId());
        Set<String> roles = permissionService.getRoleCodes(user.getId());
        return new CurrentPrincipal(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getTenantId(), "web", user.getTokenVersion(), roles, permissions);
    }

    private boolean isLocked(SysUserDO user) {
        return "LOCKED".equals(user.getStatus())
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()));
    }

    private void handleLoginFailure(SysUserDO user) {
        int failed = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
        user.setFailedLoginCount(failed);
        if (failed >= jwtProperties.getMaxLoginFailures()) {
            user.setStatus("LOCKED");
            user.setLockedUntil(LocalDateTime.now().plus(jwtProperties.getLockDuration()));
            log.warn("账号锁定 username={} 失败次数={}", user.getUsername(), failed);
        }
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, user.getId(), user);
    }

    private void resetLoginFailure(SysUserDO user) {
        if (user.getFailedLoginCount() != null && user.getFailedLoginCount() > 0) {
            user.setFailedLoginCount(0);
            user.setLockedUntil(null);
        }
        user.setLastLoginAt(LocalDateTime.now());
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, user.getId(), user);
    }
}
