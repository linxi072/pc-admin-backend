package com.acme.scaffold.security.permission;

import com.acme.scaffold.security.context.SecurityContextFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

/**
 * 数据权限切面：把 {@link DataScope} 标注的方法与「范围解析」解耦。
 *
 * <p>此前 {@code DataScope} 注解零使用，每个需要过滤的 Service 都要自己调
 * {@link DataScopeProvider#resolve}、自己判空、自己拼条件——漏一处就是越权。
 * 改为切面统一解析并写入 {@link DataScopeContext}，Service 侧只消费条件。
 *
 * <p>无登录主体（定时任务、内部调用）按「不限制」处理并记 DEBUG 日志：
 * 这类调用没有"谁在看"的语义，强行套用用户范围反而会让定时任务查不到数据。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DataScopeAspect {

    private final DataScopeProvider dataScopeProvider;
    private final SecurityContextFacade securityContextFacade;

    @Around("@annotation(dataScope) || @within(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        if (dataScope == null) {
            dataScope = AnnotationUtils.findAnnotation(
                    ((MethodSignature) joinPoint.getSignature()).getMethod(), DataScope.class);
        }
        if (dataScope == null) {
            return joinPoint.proceed();
        }
        DataScopeResult result = resolve(dataScope.resourceCode());
        Long userId = securityContextFacade.getCurrentPrincipal()
                .map(p -> p.userId())
                .orElse(null);
        DataScopeContext.set(new DataScopeContext.Scope(userId, result));
        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    private DataScopeResult resolve(String resourceCode) {
        return securityContextFacade.getCurrentPrincipal()
                .map(p -> dataScopeProvider.resolve(p.userId(), resourceCode))
                .orElseGet(() -> {
                    log.debug("无登录主体，数据范围按不限制处理: resourceCode={}", resourceCode);
                    return DataScopeResult.all();
                });
    }
}
