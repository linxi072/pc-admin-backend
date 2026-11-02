package com.acme.scaffold.security.permission;

import java.util.List;

/**
 * 字段级数据权限资源视图（安全模块对外契约，端口）。记录某资源编码下受控的字段清单，
 * 由 {@link ColumnPermissionResourceRegistry} 扫描 {@code @ColumnPermission} 得来。
 *
 * @param resourceCode 资源编码（与 @DataScope 口径一致）
 * @param resourceName 资源展示名
 * @param fields       受控字段清单（可能跨多个方法归集、按字段名去重）
 */
public record ColumnPermissionResourceView(String resourceCode, String resourceName,
                                           List<ColumnPermissionFieldView> fields) {
}
