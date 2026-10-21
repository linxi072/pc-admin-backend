package com.acme.scaffold.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 数据权限注解（扩展点）。标注在查询方法上，由 {@link DataScopeProvider} 解析允许访问的机构集合。
 * 实际 SQL 注入可基于 MyBatis 拦截器或手动在 Service 内调用 Provider 实现。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 资源编码，例如 system:user。 */
    String resourceCode();
}
