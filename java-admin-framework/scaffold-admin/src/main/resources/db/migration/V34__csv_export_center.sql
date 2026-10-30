-- V34 CSV 导出中心（系统管理 → 导出中心）
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:export:read）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 菜单：导出中心（挂在「系统管理」1002 下，sort_no=100 置于末尾）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1090, 0, 1002, 'system:export', '导出中心', 'MENU', '/system/export', 'system/ExportCenter', 'system:export:read', 'Download', 1, 0, 100, 'ACTIVE', NOW(3), NOW(3));

-- 2) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1090;
