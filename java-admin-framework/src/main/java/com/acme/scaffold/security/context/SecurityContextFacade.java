package com.acme.scaffold.security.context;

import java.util.Optional;

/**
 * 安全上下文门面：解耦业务代码与具体安全框架实现，便于测试与替换。
 */
public interface SecurityContextFacade {

    Optional<CurrentPrincipal> getCurrentPrincipal();

    CurrentPrincipal requireCurrentPrincipal();
}
