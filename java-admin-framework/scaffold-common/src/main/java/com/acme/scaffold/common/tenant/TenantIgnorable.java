package com.acme.scaffold.common.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法/类为「跨租户旁路」：在此作用域内，jOOQ 租户强制隔离（由 {@code JooqWriters} 经
 * {@code TenantEnforcement} 单点裁决）不追加 tenant_id 过滤，允许平台管理员或内部通道
 * 访问跨租户数据（如平台维表、跨租户统计）。
 *
 * <p>由 {@link TenantBypassAspect} 在方法执行期间置位 {@link TenantContext#isBypass()}，
 * 仅当已设置租户上下文时生效；未设置上下文时直接放行（不误设平台旁路）。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantIgnorable {

    /** 旁路原因说明（仅文档用途）。 */
    String value() default "";
}
