package com.acme.scaffold.config.datasource;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * @WithDataSource 切面：在目标方法执行前切换路由上下文，结束后还原为调用前状态。
 *
 * <p>置 {@link Ordered#HIGHEST_PRECEDENCE}，使其包裹在事务切面之外，
 * 保证 @Transactional 获取连接时已按目标数据源解析（避免回落主库）。
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DataSourceSwitchAspect {

    @Around("@annotation(withDataSource)")
    public Object around(ProceedingJoinPoint pjp, WithDataSource withDataSource) throws Throwable {
        String previous = DataSourceContextHolder.get();
        try {
            if (withDataSource.value().isEmpty()) {
                DataSourceContextHolder.clear();
            } else {
                DataSourceContextHolder.set(withDataSource.value());
            }
            return pjp.proceed();
        } finally {
            if (previous == null) {
                DataSourceContextHolder.clear();
            } else {
                DataSourceContextHolder.set(previous);
            }
        }
    }
}
