package com.acme.scaffold.security.permission;

/**
 * 字段级数据权限的处理策略。
 *
 * <ul>
 *   <li>{@link #MASK}：脱敏。保留字段类型特征的前提下替换敏感部分；具体算法由
 *       {@code @ColumnField#sensitiveType()} 驱动（复用框架级 {@code SensitiveMasker}），
 *       未指定敏感类型时回落通用掩码（保留首尾、掩去中间）。</li>
 *   <li>{@link #HIDE}：隐藏。将字段置空（null），前端不可见明文。</li>
 * </ul>
 */
public enum ColumnStrategy {
    /** 脱敏：按敏感类型算法替换敏感部分。 */
    MASK,
    /** 隐藏：置空（null）。 */
    HIDE
}
