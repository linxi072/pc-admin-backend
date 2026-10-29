-- 修正 M41 字段/数据权限统一配置页菜单缺失（V61 与 V58 误用同一菜单主键 1128）。
-- 受 INSERT IGNORE 主键约束，V61 的 permission-policy 菜单被静默忽略（1128 已被 form-definition 占用），
-- 导致 /system/permission-policy 路由在菜单守卫中不可达。本迁移以独立主键 1130 补种该菜单并绑定 SUPER_ADMIN。
-- 幂等：INSERT IGNORE + SELECT 绑定，可重复执行；表结构（sys_role_column_permission）已在 V61 正常创建，不涉及。
-- 设计要点：
--   1) 与 M31 行级数据权限（sys_role_data_scope）对称：此处为列级（字段级）的「角色 × 资源 × 字段」分配；
--   2) 读权限复用 system:column-permission:read（已由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）；
--   3) route_path 须与前端路由（system/permission-policy）严格一致，否则路由守卫会重定向到工作台。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1130, 0, 1002, 'system:permission-policy', '字段数据权限', 'MENU', '/system/permission-policy', 'system/PermissionPolicyView', 'system:column-permission:read', 'Lock', 1, 0, 118, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1130;
