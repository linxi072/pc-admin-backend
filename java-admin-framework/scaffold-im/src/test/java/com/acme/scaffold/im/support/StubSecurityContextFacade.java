package com.acme.scaffold.im.support;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;

import java.util.Optional;
import java.util.Set;

/**
 * 测试用安全上下文门面桩：固定返回一个登录主体。提供含参构造以便模拟不同用户（测试未读计数、已读回执、成员权限）。
 */
public class StubSecurityContextFacade implements SecurityContextFacade {

    private final CurrentPrincipal principal;

    public StubSecurityContextFacade() {
        this(1L, "tester", "测试员");
    }

    public StubSecurityContextFacade(Long userId, String username, String displayName) {
        this.principal = new CurrentPrincipal(userId, username, displayName, 0L, "web", 1,
                Set.of(), Set.of());
    }

    @Override
    public Optional<CurrentPrincipal> getCurrentPrincipal() {
        return Optional.of(principal);
    }

    @Override
    public CurrentPrincipal requireCurrentPrincipal() {
        return principal;
    }
}
