-- =============================================================
-- V19 使用报表菜单与接口资源种子
-- 目的：使「使用报表」作为独立页面出现在侧边栏，并授予 SUPER_ADMIN 访问权限，
--       以支撑运营分析（访问量 / 功能使用频次 / 活跃用户数）。
-- 说明：
--   - 菜单 route_path=/report/usage 与前端路由（pc-admin-vue3-vite/src/router/index.js）保持一致；
--   - 权限码 report:usage:read 同时登记到 sys_menu（驱动侧边栏可见性）与
--     sys_api_resource（驱动 @PreAuthorize 与「接口资源管理」目录），二者分别经
--     sys_role_menu / sys_role_api 绑定到 SUPER_ADMIN(role_id=1)。
--   - 全部走 INSERT IGNORE / NOT EXISTS，重复执行结果一致（幂等）。
-- =============================================================

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1030, 0, 0, 'report-usage', '使用报表', 'MENU', '/report/usage', NULL, 'report:usage:read', 'DataAnalysis', 1, 0, 50, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
  (900000000101, 0, '使用报表（访问量 / 功能使用频次 / 活跃用户数）', 'report:usage:read', 'GET', '/api/workbench/report', 'UsageReportController#report', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- SUPER_ADMIN 绑定「使用报表」菜单与接口资源（tenant_id, role_id, menu_id / api_id 为主键或唯一键）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id = 1030;

INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, id, CURRENT_TIMESTAMP(3) FROM sys_api_resource WHERE id = 900000000101;
