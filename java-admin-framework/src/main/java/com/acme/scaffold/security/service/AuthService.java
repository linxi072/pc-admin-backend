package com.acme.scaffold.security.service;

import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.jwt.JwtProperties;
import com.acme.scaffold.security.context.CurrentPrincipal;
import com.acme.scaffold.security.context.SecurityContextFacade;
import com.acme.scaffold.security.captcha.CaptchaProperties;
import com.acme.scaffold.security.captcha.CaptchaService;
import com.acme.scaffold.security.dto.LoginCommand;
import com.acme.scaffold.security.dto.RefreshCommand;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.jwt.JwtProvider;
import com.acme.scaffold.security.log.LoginLogRecorder;
import com.acme.scaffold.security.password.ForgotPasswordRules;
import com.acme.scaffold.security.password.LockoutRules;
import com.acme.scaffold.security.password.PasswordHistoryService;
import com.acme.scaffold.security.password.PasswordPolicy;
import com.acme.scaffold.security.permission.PermissionService;
import com.acme.scaffold.security.token.service.RefreshTokenService;
import com.acme.scaffold.security.token.rules.SessionLimitRules;
import com.acme.scaffold.security.vo.TokenView;
import com.acme.scaffold.system.entity.SysLoginRestrictionDO;
import com.acme.scaffold.system.entity.SysUserDO;
import com.acme.scaffold.system.service.OnlineUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * 认证应用服务：登录、刷新、登出，负责密码校验、账号锁定、令牌签发与轮换。
 * 持久层由 MyBatis-Plus 迁移为 jOOQ。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 限制解除时间展示格式（yyyy-MM-dd HH:mm）。 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

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
    private final LoginLogRecorder loginLogRecorder;
    private final OnlineUserService onlineUserService;

    /**
     * 账号密码登录。
     *
     * <p><b>{@code noRollbackFor = BusinessException} 是刻意为之</b>：登录失败路径唯一的写操作是
     * {@link #handleLoginFailure(SysUserDO)}（累计失败次数 / 置 LOCKED），而它执行后必然抛出
     * BusinessException 告知调用方失败。默认回滚规则会让这笔写入随异常一并撤销，导致
     * 「失败 N 次锁定账号」与渐进式锁定（设计文档 §5.3）<b>完全失效</b>。成功路径不抛
     * BusinessException，提交语义不受影响。</p>
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public TokenView login(LoginCommand command, String ip, String userAgent, String deviceId) {
        // 验证码校验优先于密码校验：避免在验证码缺失/错误时消耗登录失败计数导致误锁账号
        if (captchaProperties.isEnabled()) {
            if (command.captchaToken() == null || command.captchaAnswer() == null) {
                throw loginFailure(command.username(), null, SecurityErrorCode.CAPTCHA_REQUIRED,
                        "请先获取并完成验证码", ip, userAgent);
            }
            if (!captchaService.verify(command.captchaToken(), command.captchaAnswer())) {
                throw loginFailure(command.username(), null, SecurityErrorCode.CAPTCHA_INVALID,
                        "验证码错误或已失效", ip, userAgent);
            }
        }
        SysUserDO user = JooqWriters.fetchOne(dsl, JooqTables.SYS_USER, SysUserDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("username", String.class).eq(command.username())));
        if (user == null) {
            throw loginFailure(command.username(), null, SecurityErrorCode.BAD_CREDENTIALS,
                    "用户名或密码错误", ip, userAgent);
        }
        if (isLocked(user)) {
            throw loginFailure(user.getUsername(), user.getId(), SecurityErrorCode.ACCOUNT_LOCKED,
                    "账号已锁定，请稍后再试", ip, userAgent);
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw loginFailure(user.getUsername(), user.getId(), SecurityErrorCode.ACCOUNT_DISABLED,
                    "账号已禁用", ip, userAgent);
        }
        if (!passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            handleLoginFailure(user);
            throw loginFailure(user.getUsername(), user.getId(), SecurityErrorCode.BAD_CREDENTIALS,
                    "用户名或密码错误", ip, userAgent);
        }
        // 设计文档 §5.3：被标记为「需改密」(password_expired=1) 的用户必须先改密再登录
        if (user.getPasswordExpired() != null && user.getPasswordExpired() == 1) {
            throw loginFailure(user.getUsername(), user.getId(), SecurityErrorCode.MUST_CHANGE_PASSWORD,
                    "请修改初始/重置密码后再登录", ip, userAgent);
        }
        // V27 在线用户管理：临时登录限制校验。置于密码校验之后——
        // 已认证方才有权得知「被限制」及其解除时间，未认证方只能得到通用错误，防账号状态探测。
        SysLoginRestrictionDO restriction = onlineUserService.findActiveRestriction(user.getId());
        if (restriction != null) {
            throw loginFailure(user.getUsername(), user.getId(), SecurityErrorCode.ACCOUNT_RESTRICTED,
                    restrictionMessage(restriction), ip, userAgent);
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
                throw loginFailure(user.getUsername(), user.getId(), SecurityErrorCode.TOO_MANY_SESSIONS,
                        "同一账号同时登录设备数已达上限 " + maxSessions, ip, userAgent);
            } else if (decision == SessionLimitRules.Decision.EVICT_OLDEST) {
                // 保留 maxSessions-1 个最新会话，其余（含本次将签发的）总计不超过上限
                refreshTokenService.revokeOldestSessions(user.getId(), maxSessions - 1);
            }
        }

        CurrentPrincipal principal = buildPrincipal(user);
        String accessToken = jwtProvider.generateAccessToken(principal);
        String refreshToken = refreshTokenService.issue(user.getId(), "web", deviceId, userAgent, ip);
        // V27 在线用户管理：登记在线会话（upsert，供列表跟踪 / 强制下线 / 心跳）
        onlineUserService.registerSession(user.getId(), user.getUsername(), user.getDisplayName(),
                deviceId, "web", ip, userAgent);
        // 登录成功落审计日志（REQUIRES_NEW 独立事务，供「报表管理 - 登录统计」取数）
        loginLogRecorder.record(user.getUsername(), user.getId(), true, null, ip, userAgent);
        return new TokenView(accessToken, refreshToken, jwtProperties.getAccessTokenTtl().getSeconds());
    }

    /** 限制登录的面向用户提示：长期限制提示联系管理员，限时限制附带预计解除时间。 */
    private String restrictionMessage(SysLoginRestrictionDO restriction) {
        if (restriction.getExpireAt() == null) {
            return SecurityErrorCode.ACCOUNT_RESTRICTED.message() + "（长期，请联系管理员）";
        }
        return SecurityErrorCode.ACCOUNT_RESTRICTED.message() + "，预计 "
                + restriction.getExpireAt().format(DATE_TIME_FORMATTER) + " 解除";
    }

    /**
     * 记录一次登录失败审计（sys_login_log）并构造业务异常。
     *
     * <p>失败路径最终抛出 RuntimeException 导致登录主事务回滚，故失败日志必须由
     * {@link LoginLogRecorder} 以 REQUIRES_NEW 独立事务落库，否则异常登录记录将随回滚丢失，
     * 「登录统计 - 异常登录记录」永远空态。</p>
     *
     * @param username  尝试登录的用户名（用户不存在时为客户端的输入值）
     * @param userId    用户主键（用户不存在时传 null）
     * @param code      安全错误码（其 code() 即 failure_code，如 AUTH_001）
     * @param message   面向客户端的提示信息
     * @param ip        客户端 IP
     * @param userAgent 客户端 UA
     * @return 构造好的业务异常（由调用方 throw）
     */
    private BusinessException loginFailure(String username, Long userId, SecurityErrorCode code,
                                           String message, String ip, String userAgent) {
        loginLogRecorder.record(username, userId, false, code.code(), ip, userAgent);
        return new BusinessException(code, message);
    }

    @Transactional
    public TokenView refresh(RefreshCommand command) {
        Long userId = refreshTokenService.verify(command.refreshToken());
        SysUserDO user = JooqWriters.fetchById(dsl, JooqTables.SYS_USER, SysUserDO.class, userId);
        if (user == null || "DISABLED".equals(user.getStatus())) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_DISABLED, "账号已禁用");
        }
        // 刷新令牌最长可达 30 天，若不复用登录期的账号状态校验，被锁定 / 标记需改密 / 临时受限的账号
        // 仍能凭手中旧令牌持续换取新 access token，登录侧的全部拦截被绕过（历史缺陷）。
        assertUsableForTokenIssue(user);
        CurrentPrincipal principal = buildPrincipal(user);
        String accessToken = jwtProvider.generateAccessToken(principal);
        String newRefresh = refreshTokenService.rotate(command.refreshToken());
        return new TokenView(accessToken, newRefresh, jwtProperties.getAccessTokenTtl().getSeconds());
    }

    @Transactional
    public void logout() {
        Long userId = securityContextFacade.requireCurrentPrincipal().userId();
        refreshTokenService.revokeByUser(userId);
        // V27：在线会话同步置离线，管理端「在线用户」列表即时反映
        onlineUserService.markOfflineByUser(userId, OnlineUserService.REASON_LOGOUT);
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
        // V27：在线会话同步置离线，管理端「在线用户」列表即时反映
        onlineUserService.markOfflineByUser(user.getId(), OnlineUserService.REASON_PASSWORD_CHANGED);
    }

    /**
     * 「忘记密码」自助重置：以「用户名 + 手机号/邮箱核验」完成身份认证，无需原密码。
     *
     * <p>与 {@link #changePassword} 的分工：后者面向「知道原密码」的主动改密；
     * 本方法面向「忘记原密码」的自助找回。两者成功后的状态收敛完全一致
     * （新口令生效 + password_expired 清零 + 密码历史留痕 + 旧凭证全部失效）。
     *
     * <p>安全约定：
     * <ul>
     *   <li><b>防账号枚举</b>：「用户不存在」与「验证信息不匹配」返回同一错误
     *       （{@code AUTH_001 用户名或验证信息不匹配}），攻击者无法通过差异提示枚举有效账号；</li>
     *   <li><b>接口限流</b>：{@code /api/auth/forgot-password} 与 login 同阈值限流
     *       （见 {@code RateLimitFilter}），防撞库；</li>
     *   <li><b>强度与历史</b>：与改密链路共用 {@link PasswordPolicy} 与「禁止复用最近 5 次」。</li>
     * </ul>
     *
     * @param username    用户名
     * @param newPassword 新密码
     * @param mobile      手机号（与库中记录匹配，二选一）
     * @param email       邮箱（与库中记录匹配，二选一）
     */
    @Transactional
    public void forgotPassword(String username, String newPassword, String mobile, String email) {
        // 验证凭据充分性：手机号/邮箱至少提供一个（前端同效校验，服务端为权威）
        if (!ForgotPasswordRules.hasVerifier(mobile, email)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "请填写手机号或邮箱用于身份验证");
        }
        // 密码强度策略：忘记密码重置同样要求合规
        PasswordPolicy.validate(newPassword);

        SysUserDO user = JooqWriters.fetchOne(dsl, JooqTables.SYS_USER, SysUserDO.class,
                DSL.and(JooqWriters.notDeleted(JooqTables.SYS_USER),
                        JooqTables.SYS_USER.field("username", String.class).eq(username)));
        // 统一返回 BAD_CREDENTIALS，防止账号枚举（不区分「用户不存在」与「验证信息不匹配」）
        if (user == null || !ForgotPasswordRules.verified(user.getMobile(), user.getEmail(), mobile, email)) {
            throw new BusinessException(SecurityErrorCode.BAD_CREDENTIALS, "用户名或验证信息不匹配");
        }
        if ("LOCKED".equals(user.getStatus())) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_LOCKED, "账号已锁定，请稍后再试");
        }
        if ("DISABLED".equals(user.getStatus())) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_DISABLED, "账号已禁用");
        }
        // 设计文档 §5.3：禁止复用最近 5 次密码
        if (passwordHistoryService.isReused(user.getId(), newPassword)) {
            throw new BusinessException(SecurityErrorCode.PASSWORD_REUSED, "不能与最近使用的密码相同");
        }

        String encoded = passwordEncoder.encode(newPassword);
        SysUserDO patch = new SysUserDO();
        patch.setPasswordHash(encoded);
        patch.setPasswordChangedAt(LocalDateTime.now());
        // 重置即赋予可用口令：清除「需改密」标记（与 UserService.resetPassword 语义一致）
        patch.setPasswordExpired(0);
        patch.setFailedLoginCount(0);
        patch.setLockedUntil(null);
        patch.setTokenVersion((user.getTokenVersion() == null ? 1 : user.getTokenVersion()) + 1);
        JooqWriters.updateById(dsl, JooqTables.SYS_USER, user.getId(), patch);

        // 记录本次重置后的口令到密码历史，纳入「禁止复用最近 5 次」基线
        passwordHistoryService.record(user.getId(), encoded);
        // 重置后吊销全部刷新令牌，旧设备全部下线
        refreshTokenService.revokeByUser(user.getId());
        // V27：在线会话同步置离线，管理端「在线用户」列表即时反映
        onlineUserService.markOfflineByUser(user.getId(), OnlineUserService.REASON_PASSWORD_CHANGED);
    }

    private CurrentPrincipal buildPrincipal(SysUserDO user) {
        Set<String> permissions = permissionService.getPermissions(user.getId());
        Set<String> roles = permissionService.getRoleCodes(user.getId());
        return new CurrentPrincipal(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getTenantId(), "web", user.getTokenVersion(), roles, permissions);
    }

    /**
     * 账号状态守卫：签发或续期令牌前必须通过的校验（登录与刷新共用同一口径）。
     *
     * <p>刷新令牌有效期远长于 access token，若不在续期时复核账号状态，被锁定 / 标记需改密 /
     * 临时受限的账号可凭手中旧令牌持续换取新凭证，使登录侧拦截形同虚设。</p>
     *
     * @param user 目标账号
     * @throws BusinessException 账号已锁定 / 需改密 / 处于临时登录限制中
     */
    private void assertUsableForTokenIssue(SysUserDO user) {
        if (isLocked(user)) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_LOCKED, "账号已锁定，请稍后再试");
        }
        if (user.getPasswordExpired() != null && user.getPasswordExpired() == 1) {
            throw new BusinessException(SecurityErrorCode.MUST_CHANGE_PASSWORD, "请修改初始/重置密码后再登录");
        }
        SysLoginRestrictionDO restriction = onlineUserService.findActiveRestriction(user.getId());
        if (restriction != null) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_RESTRICTED, restrictionMessage(restriction));
        }
    }

    private boolean isLocked(SysUserDO user) {
        return "LOCKED".equals(user.getStatus())
                || (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()));
    }

    private void handleLoginFailure(SysUserDO user) {
        int failed = (user.getFailedLoginCount() == null ? 0 : user.getFailedLoginCount()) + 1;
        user.setFailedLoginCount(failed);
        // 设计文档 §5.3：渐进式锁定——达到阈值后锁定时长随失败次数递增，封顶 lockDurationMax
        if (LockoutRules.shouldLock(failed, jwtProperties.getMaxLoginFailures())) {
            Duration dur = LockoutRules.computeDuration(failed, jwtProperties.getMaxLoginFailures(),
                    jwtProperties.getLockDuration(), jwtProperties.getLockDurationMax());
            user.setStatus("LOCKED");
            user.setLockedUntil(LocalDateTime.now().plus(dur));
            log.warn("账号锁定 username={} 失败次数={} 时长={}", user.getUsername(), failed, dur);
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
