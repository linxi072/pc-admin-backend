package com.acme.scaffold.system.service;

import com.acme.scaffold.common.context.SecurityContextFacade;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import com.acme.scaffold.jooq.dsl.JooqTables;
import com.acme.scaffold.jooq.dsl.JooqWriters;
import com.acme.scaffold.security.error.SecurityErrorCode;
import com.acme.scaffold.security.password.PasswordHistoryService;
import com.acme.scaffold.security.password.PasswordPolicy;
import com.acme.scaffold.security.token.service.RefreshTokenService;
import com.acme.scaffold.security.token.service.TokenVersionService;
import com.acme.scaffold.system.dto.ChangePasswordRequest;
import com.acme.scaffold.system.entity.SysUserDO;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 个人中心「自助改密」子域（从 {@link ProfileService} 抽离，C2 重构）。
 *
 * <p>职责：校验原密码 → 强度策略 → 复用比对 → 落库新口令 → 自增 token_version
 * 使全部旧令牌失效 → 记录历史 → 吊销全部刷新令牌。行为与抽离前完全一致，
 * {@link ProfileService} 仅做委托，公开方法签名不变。
 */
@Service
@RequiredArgsConstructor
public class ProfilePasswordService {
    private static final JooqTables.TableRef USER = JooqTables.SYS_USER;

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;
    private final SecurityContextFacade securityContextFacade;
    private final RefreshTokenService refreshTokenService;
    private final PasswordHistoryService passwordHistoryService;
    private final TokenVersionService tokenVersionService;

    /**
     * 自助改密：必须校验原密码；成功后自增 token_version 使全部旧令牌失效，
     * 同时吊销所有刷新令牌，强制重新登录。
     */
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        SysUserDO current = loadCurrentUser();
        if (!passwordEncoder.matches(request.oldPassword(), current.getPasswordHash())) {
            throw new BusinessException(SecurityErrorCode.BAD_CREDENTIALS, "原密码不正确");
        }
        // 校验顺序：原密码 → 强度策略 → 复用比对（策略先行，给出可操作指引）
        PasswordPolicy.validate(request.newPassword());
        if (passwordEncoder.matches(request.newPassword(), current.getPasswordHash())) {
            throw new BusinessException(SecurityErrorCode.PASSWORD_REUSED, "新密码不能与原密码相同");
        }
        if (passwordHistoryService.isReused(current.getId(), request.newPassword())) {
            throw new BusinessException(SecurityErrorCode.PASSWORD_REUSED, "不能与最近使用的密码相同");
        }

        String encoded = passwordEncoder.encode(request.newPassword());
        SysUserDO patch = new SysUserDO();
        patch.setPasswordHash(encoded);
        patch.setPasswordChangedAt(LocalDateTime.now());
        patch.setPasswordExpired(0);
        patch.setFailedLoginCount(0);
        patch.setLockedUntil(null);
        JooqWriters.updateById(dsl, USER, current.getId(), patch);

        // 统一走 TokenVersionService 自增：同步失效进程内 30s 缓存，避免改密后旧 token 短暂有效
        tokenVersionService.bumpUser(current.getId());

        passwordHistoryService.record(current.getId(), encoded);
        // 改密后吊销全部刷新令牌：即便攻击者持有旧 refresh token 也无法续期
        refreshTokenService.revokeByUser(current.getId());
    }

    private SysUserDO loadCurrentUser() {
        Long userId = securityContextFacade.requireCurrentPrincipal().userId();
        SysUserDO user = JooqWriters.fetchById(dsl, USER, SysUserDO.class, userId);
        if (user == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }
}
