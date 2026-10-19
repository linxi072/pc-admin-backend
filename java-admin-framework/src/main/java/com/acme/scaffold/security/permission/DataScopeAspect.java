package com.acme.scaffold.security.permission;

import com.acme.scaffold.security.context.SecurityContextFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
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
 *
 * <p>{@link Order#HIGHEST_PRECEDENCE}：本切面须为最外层通知。机构树等热点读在
 * {@code @Cacheable} 上以「数据权限范围签名」为键，缓存键在缓存拦截器内计算，
 * 必须保证此时 {@link DataScopeContext} 已由本切面写入；若切面晚于缓存拦截器执行，
 * 键会恒为 {@code ALL} 而把受限用户的树误并入管理员缓存，造成越权可见。
 */
@Slf4j
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class DataScopeAspect {

    private final DataScopeProvider dataScopeProvider;
    private final SecurityContextFacade securityContextFacade;

    // 单点 @annotation 绑定 + 手动解析注解（含类级别），避免 "@annotation(x) || @within(x)" 这种
    // OR 组合绑定切点带来的参数绑定失败：当连接点仅经 @within 命中（类级别标注）时，AspectJ 无法
    // 注入内部 JoinPointMatch，会抛 "Required to bind 2 arguments, but only bound 1
    // (JoinPointMatch was NOT bound in invocation)"，导致用户/部门/待办/流程等所有模块同时异常。
    // AnnotationUtils.findAnnotation 会沿方法→声明类向上查找，方法级与类级 @DataScope 均可命中。
    @Around("@annotation(com.acme.scaffold.security.permission.DataScope)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        DataScope dataScope = AnnotationUtils.findAnnotation(
                ((MethodSignature) joinPoint.getSignature()).getMethod(), DataScope.class);
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
