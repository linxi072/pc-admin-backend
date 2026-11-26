package com.acme.scaffold.security.oauth2;

import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.password.LockoutRules;
import com.acme.scaffold.security.permission.PermissionService;
import com.acme.scaffold.security.user.UserAccount;
import com.acme.scaffold.security.user.UserAccountDataPort;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Component;

/**
 * OAuth2 {@code password} 授权模式的轻量用户认证（零新增依赖）。
 *
 * <p>职责为「校验用户名/密码 + 强制改密门禁 + 聚合权限」，不触发登录侧副作用（验证码、账号锁定计数、
 * 刷新令牌签发、在线会话登记、登录审计），这些属于交互式登录链路，OAuth2 机器调用不需要。
 * 身份状态判定与登录/刷新同源，复用 {@link PermissionService} 聚合 {@code authorities}/{@code roles}。</p>
 *
 * <p>账号读取经 {@link UserAccountDataPort}（system 适配器实现）完成，security 不再直连
 * {@code system.entity.SysUser}（B1/B5 同款防腐层约束）。</p>
 */
@Component
public class OAuth2UserAuthenticator {

    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final UserAccountDataPort userAccountDataPort;

    public OAuth2UserAuthenticator(PasswordEncoder passwordEncoder, PermissionService permissionService,
                                  UserAccountDataPort userAccountDataPort) {
        this.passwordEncoder = passwordEncoder;
        this.permissionService = permissionService;
        this.userAccountDataPort = userAccountDataPort;
    }

    public record AuthenticatedUser(Long userId, String username, String displayName, Long tenantId,
                                    int tokenVersion, Set<String> authorities, Set<String> roles) {
    }

    /**
     * 校验用户名/密码并返回认证主体；失败统一以 {@link SecurityErrorCode#BAD_CREDENTIALS} 响应（防账号枚举）。
     */
    public AuthenticatedUser authenticate(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BusinessException(SecurityErrorCode.BAD_CREDENTIALS, "用户名或密码错误");
        }
        UserAccount user = userAccountDataPort.loadByUsername(username);
        if (user == null) {
            throw new BusinessException(SecurityErrorCode.BAD_CREDENTIALS, "用户名或密码错误");
        }
        // 与 AuthService 交互式登录同源：带 lockedUntil 的临时锁到期后应放行，
        // 人工长期锁（LOCKED 且无到期时间）仍持续阻断。
        if ("DISABLED".equals(user.status())
                || LockoutRules.isLocked(user.status(), user.lockedUntil(), LocalDateTime.now())) {
            throw new BusinessException(SecurityErrorCode.ACCOUNT_DISABLED, "账号已禁用或锁定");
        }
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new BusinessException(SecurityErrorCode.BAD_CREDENTIALS, "用户名或密码错误");
        }
        // 与交互式登录/令牌刷新同源：初始密码或管理员重置后的密码必须先经公开改密端点修改。
        // 放在密码校验之后，未认证方无法借此探测账号状态。
        if (user.passwordExpired() != null && user.passwordExpired() == 1) {
            throw new BusinessException(SecurityErrorCode.MUST_CHANGE_PASSWORD, "请修改初始/重置密码后再登录");
        }
        Set<String> authorities = permissionService.getPermissions(user.id());
        Set<String> roles = permissionService.getRoleCodes(user.id());
        int tokenVersion = user.tokenVersion() == null ? 1 : user.tokenVersion();
        return new AuthenticatedUser(user.id(), user.username(), user.displayName(),
                user.tenantId(), tokenVersion, authorities, roles);
    }
}
