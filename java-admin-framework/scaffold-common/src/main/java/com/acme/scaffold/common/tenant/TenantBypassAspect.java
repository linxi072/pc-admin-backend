package com.acme.scaffold.common.tenant;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 跨租户旁路切面：为标注 {@link TenantIgnorable} 的方法临时置位
 * {@link TenantContext#isBypass()}，使 jOOQ 租户隔离监听器跳过 tenant_id 过滤。
 *
 * <p>典型场景：平台管理员维护 sys_tenant / sys_menu 等平台维表、跨租户聚合统计。
 * 仅当已设置租户上下文时生效；未设置（如定时任务、内部无主体调用）直接放行，
 * 避免把无作用域调用误判为平台旁路、造成隐式越权。</p>
 */
@Slf4j
@Aspect
@Component
public class TenantBypassAspect {

    @Around("@annotation(com.acme.scaffold.common.tenant.TenantIgnorable)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!TenantContext.isSet()) {
            return joinPoint.proceed();
        }
        return TenantContext.runWithBypass(() -> {
            try {
                return joinPoint.proceed();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
    }
}
