package com.acme.scaffold.security.permission;

import com.acme.scaffold.common.sensitive.SensitiveType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * 受控字段声明：作为 {@link ColumnPermission#fields()} 的数组成员值使用（不单独标注在类/方法上）。
 *
 * <p>每个受控字段声明其 Java 属性名、展示名、处理策略与（可选）敏感类型。敏感类型驱动
 * 复用 {@link com.acme.scaffold.common.sensitive.SensitiveMasker} 的脱敏算法；
 * {@code MASK} 策略下未指定敏感类型时回落通用掩码。</p>
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface ColumnField {

    /** 字段名（Java 属性名，驼峰，须与目标 DTO/实体的字段名一致）。 */
    String name();

    /** 字段展示名（用于配置页展示；为空回退为 {@link #name()}）。 */
    String label() default "";

    /** 处理策略，默认脱敏（MASK）。 */
    ColumnStrategy strategy() default ColumnStrategy.MASK;

    /**
     * 敏感类型（仅 {@code MASK} 策略生效），驱动脱敏算法；缺省 {@code NONE} 表示
     * 回落通用掩码（保留首 3 末 4，中间以 {@code *} 替换）。
     */
    SensitiveType sensitiveType() default SensitiveType.NONE;
}
