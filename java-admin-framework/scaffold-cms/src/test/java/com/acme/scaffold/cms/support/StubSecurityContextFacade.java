package com.acme.scaffold.cms.support;

import com.acme.scaffold.common.context.CurrentPrincipal;
import com.acme.scaffold.common.context.SecurityContextFacade;

import java.util.Optional;
import java.util.Set;

/**
 * 测试用安全上下文门面桩：固定返回一个名为「测试员」的登录主体，避免依赖 Spring Security 装配。
 */
public class StubSecurityContextFacade implements SecurityContextFacade {

    private final CurrentPrincipal principal = new CurrentPrincipal(
            1L, "tester", "测试员", 0L, "web", 1, Set.of("CMS_ADMIN"), Set.of("cms:category:manage"));

    @Override
    public Optional<CurrentPrincipal> getCurrentPrincipal() {
        return Optional.of(principal);
    }

    @Override
    public CurrentPrincipal requireCurrentPrincipal() {
        return principal;
    }
}
