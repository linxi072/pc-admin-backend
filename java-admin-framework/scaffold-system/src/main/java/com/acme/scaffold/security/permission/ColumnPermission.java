package com.acme.scaffold.security.permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级数据权限注解（扩展点）。标注在返回敏感数据的查询方法上，
 * 由 {@link ColumnPermissionResourceRegistry} 启动时扫描归集，供「字段级权限配置」页动态渲染；
 * 运行时由 {@link ColumnPermissionApplier} 按当前主体的列权限对受控字段脱敏/隐藏。
 *
 * <p><b>与 {@link DataScope} 的关系</b>：{@code @DataScope} 控制「能看哪些行的数据」（行级），
 * 本注解控制「能看每行的哪些字段」（列级），二者正交、可叠加。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ColumnPermission {

    /** 资源编码，须与 {@link DataScope#resourceCode()} 口径一致，例如 system:user。 */
    String resourceCode();

    /** 资源展示名，用于字段级权限配置页展示；为空时回退为 {@link #resourceCode()}。 */
    String name() default "";

    /** 受控字段清单（脱敏/隐藏的字段）。 */
    ColumnField[] fields() default {};
}
