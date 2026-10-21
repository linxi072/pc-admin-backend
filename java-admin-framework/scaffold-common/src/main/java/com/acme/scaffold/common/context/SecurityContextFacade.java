package com.acme.scaffold.common.context;

import java.util.Optional;

/**
 * 安全上下文门面：解耦业务代码与具体安全框架实现，便于测试与替换。
 *
 * <p><b>模块边界（V1.0.3 拆分）</b>：本接口与 {@link CurrentPrincipal} 是 common 与 system
 * 两个模块共用的「端口」，故留在 scaffold-common；基于 Spring Security 的适配器
 * {@code SecurityContextFacadeImpl} 留在 scaffold-system 的 {@code security.context} 包，
 * 由 Spring 容器注入。依赖方向保持 common ← system，不成环。
 */
public interface SecurityContextFacade {

    Optional<CurrentPrincipal> getCurrentPrincipal();

    CurrentPrincipal requireCurrentPrincipal();
}
