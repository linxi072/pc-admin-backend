package com.acme.scaffold.security.application;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.JooqTables;
import com.acme.scaffold.jooq.JooqWriters;
import com.acme.scaffold.security.config.JwtProperties;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.security.captcha.CaptchaProperties;
import com.acme.scaffold.security.captcha.CaptchaService;
import com.acme.scaffold.security.dto.LoginCommand;
import com.acme.scaffold.security.dto.RefreshCommand;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.jwt.JwtProvider;
import com.acme.scaffold.security.password.LockoutRules;
import com.acme.scaffold.security.password.PasswordHistoryService;
import com.acme.scaffold.security.password.PasswordPolicy;
import com.acme.scaffold.security.permission.PermissionService;
import com.acme.scaffold.security.token.RefreshTokenService;
import com.acme.scaffold.security.token.SessionLimitRules;
import com.acme.scaffold.security.vo.TokenView;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
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
    private final CaptchaService captchaService;
    private final CaptchaProperties captchaProperties;
    private final PasswordHistoryService passwordHistoryService;

    @Transactional
    public TokenView login(LoginCommand command, String ip, String userAgent, String deviceId) {
        // 验证码校验优先于密码校验：避免在验证码缺失/错误时消耗登录失败计数导致误锁账号
        if (captchaProperties.isEnabled()) {
            if (command.captchaToken() == null || command.captchaAnswer() == null) {
                throw new BusinessException(SecurityErrorCode.CAPTCHA_REQUIRED, "请先获取并完成验证码");
            }
            if (!captchaService.verify(command.captchaToken(), command.captchaAnswer())) {
                throw new BusinessException(SecurityErrorCode.CAPTCHA_INVALID, "验证码错误或已失效");
            }
        }
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
        // 设计文档 §5.3：被标记为「需改密」(password_expired=1) 的用户必须先改密再登录
        if (user.getPasswordExpired() != null && user.getPasswordExpired() == 1) {
            throw new BusinessException(SecurityErrorCode.MUST_CHANGE_PASSWORD, "请修改初始/重置密码后再登录");
        }
        resetLoginFailure(user);

        // 多端登录限制：控制同一账号同时在线设备数（每个活跃刷新令牌即一个在线设备）
        int maxSessions = jwtProperties.getMaxConcurrentSessions();
        if (maxSessions > 0) {
            int active = refreshTokenService.countActiveSessions(user.getId());
            SessionLimitRules.Strategy strategy = "evict_oldest".equalsIgnoreCase(jwtProperties.getSessionEvictionStrategy())
                    ? SessionLimitRules.Strategy.EVICT_OLDEST
                    : SessionLimitRules.Strategy.REJECT;
            SessionLimitRules.Decision decision = SessionLimitRules.evaluate(active, maxSessions, strategy);
            if (decision == SessionLimitRules.Decision.REJECT) {
                throw new BusinessException(SecurityErrorCode.TOO_MANY_SESSIONS,
                        "同一账号同时登录设备数已达上限 " + maxSessions);
            } else if (decision == SessionLimitRules.Decision.EVICT_OLDEST) {
                // 保留 maxSessions-1 个最新会话，其余（含本次将签发的）总计不超过上限
                refreshTokenService.revokeOldestSessions(user.getId(), maxSessions - 1);
            }
        }

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

    /**
     * 会话无关改密：闭合「首次登录 / 管理员重置后强制改密」链路。
     * <p>由「用户名 + 原密码」完成身份认证（无需已登录会话），因此被标记为
     * {@code password_expired=1}、无法走正常登录的用户，也能先改密再进入系统，
     * 避免「登录被拦却无法改密」的死锁。改密成功后清除 {@code password_expired} 并吊销全部刷新令牌。
     *
     * @param username     用户名
     * @param oldPassword  原密码（身份认证用）
     * @param newPassword  新密码
     */
    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        SysUserDO user = JooqWriters.fetchOne(dsl, JooqTables.SYS_USER, SysUserDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("username", String.class).eq(username)));
        // 统一返回 BAD_CREDENTIALS，防止账号枚举（不区分「用户不存在」与「原密码错误」）
        if (user == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new BusinessException(SecurityErrorCode.BAD_CREDENTIALS, "用户名或密码错误");
        }
        if ("LOCKED".equals(user.getStatus())) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_LOCKED, "账号已锁定，请稍后再试");
        }
        // 设计文档 §5.3：禁止复用最近 5 次密码
        if (passwordHistoryService.isReused(user.getId(), newPassword)) {
            throw new BusinessException(SecurityErrorCode.PASSWORD_REUSED, "不能与最近使用的密码相同");
        }
        PasswordPolicy.validate(newPassword);

        String encoded = passwordEncoder.encode(newPassword);
        SysUserDO patch = new SysUserDO();
        patch.setPasswordHash(encoded);
        patch.setPasswordChangedAt(LocalDateTime.now());
        patch.setPasswordExpired(0);
        patch.setFailedLoginCount(0);
        patch.setLockedUntil(null);
        patch.setTokenVersion((user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1);
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, user.getId(), patch);

        // 记录本次改密后的口令到密码历史，纳入「禁止复用最近 5 次」基线
        passwordHistoryService.record(user.getId(), encoded);
        // 改密后吊销全部刷新令牌，强制使用新口令重新登录
        refreshTokenService.revokeByUser(user.getId());
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
