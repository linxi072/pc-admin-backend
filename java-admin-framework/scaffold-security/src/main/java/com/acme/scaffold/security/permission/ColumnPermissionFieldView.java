package com.acme.scaffold.security.permission;

import com.acme.scaffold.common.sensitive.SensitiveType;

/**
 * 受控字段视图（安全模块对外契约，端口）。由 {@link ColumnPermissionResourceRegistry}
 * 从 {@code @ColumnPermission} 扫描得来，供配置页展示与运行时脱敏复用。
 *
 * @param field         字段名（Java 属性名）
 * @param label         字段展示名
 * @param strategy      处理策略（MASK/HIDE）
 * @param sensitiveType 敏感类型（仅 MASK 生效；NONE 表示通用掩码）
 */
public record ColumnPermissionFieldView(String field, String label, ColumnStrategy strategy, SensitiveType sensitiveType) {
}
