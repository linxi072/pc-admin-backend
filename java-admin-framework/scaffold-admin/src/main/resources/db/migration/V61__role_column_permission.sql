-- V61 字段级数据权限分配（M41）：sys_role_column_permission，把角色对受控字段的明文可见权
-- 以「角色 × 资源 × 字段」三元组持久化，供统一配置页（PermissionPolicyView 字段权限 Tab）读写。
--
-- 设计要点：
--   1) 与 M31 行级数据权限（sys_role_data_scope）对称：此处为列级（字段级）的「角色 × 资源 × 字段」分配。
--   2) 完整权限码 = resourceCode + ':column:' + field（如 system:user:column:mobile），
--      由 ColumnPermissionRules.columnPermissionCode 派生，与运行时 ColumnPermissionApplier 口径同源。
--   3) 鉴权：读取复用 system:role:read；保存复用 system:role:update（在角色管理上下文中操作）。
--   4) role_id 无物理外键（与 sys_role_data_scope 一致，避免级联误删），由应用层保证存在性。
--   5) 运行时脱敏是否生效由 column-permission.enforce 开关控制（默认关闭，零线上回归，同 M19）。
--   6) 依赖：V32（系统管理目录 id=1002）；ColumnPermissionController 只读资源端点（system:column-permission:read）。

CREATE TABLE IF NOT EXISTS sys_role_column_permission (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    resource_code VARCHAR(128) NOT NULL,
    field VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_resource_field (tenant_id, role_id, resource_code, field),
    KEY idx_role (tenant_id, role_id),
    KEY idx_resource (tenant_id, resource_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 菜单：字段/数据权限统一配置（M41），挂在「系统管理」1002 下，sort_no=117 紧随数据权限(116)之后。
-- route_path 须与前端路由（system/permission-policy）严格一致，否则路由守卫会重定向到工作台。
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1128, 0, 1002, 'system:permission-policy', '字段数据权限', 'MENU', '/system/permission-policy', 'system/PermissionPolicyView', 'system:column-permission:read', 'Lock', 1, 0, 117, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1128;
