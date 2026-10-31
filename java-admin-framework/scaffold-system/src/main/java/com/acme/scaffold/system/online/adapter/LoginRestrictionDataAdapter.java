package com.acme.scaffold.system.online.adapter;

import com.acme.scaffold.security.online.LoginRestriction;
import com.acme.scaffold.security.online.LoginRestrictionDataPort;
import com.acme.scaffold.system.entity.SysLoginRestrictionDO;
import com.acme.scaffold.system.service.LoginRestrictionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link LoginRestrictionDataPort} 的 system 侧适配器实现。
 *
 * <p>承载对 {@code sys_login_restriction} 的查询，将 {@code system.entity.SysLoginRestrictionDO}
 * 映射为 security 自有 {@link LoginRestriction} 快照；本类位于 {@code system} 包，允许 import
 * {@code system.entity}。</p>
 */
@Component
@RequiredArgsConstructor
public class LoginRestrictionDataAdapter implements LoginRestrictionDataPort {

    private final LoginRestrictionService loginRestrictionService;

    @Override
    public LoginRestriction findActive(Long userId) {
        if (userId == null) {
            return null;
        }
        SysLoginRestrictionDO restriction = loginRestrictionService.findActiveRestriction(userId);
        return restriction == null ? null : new LoginRestriction(restriction.getExpireAt());
    }

    @Override
    public int releaseExpired() {
        return loginRestrictionService.releaseExpired();
    }
}
