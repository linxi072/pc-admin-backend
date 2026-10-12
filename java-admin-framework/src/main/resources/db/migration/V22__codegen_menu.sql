-- =============================================================
-- V22 代码生成器菜单与接口资源种子
-- 目的：使「代码生成」作为开发工具页出现在侧边栏，并授予 SUPER_ADMIN 访问权限，
--       支撑按表结构生成 CRUD 骨架（后端六层 + 前端两文件）。
-- 说明：
--   - 菜单 route_path=/dev/codegen 与前端路由（pc-admin-vue3-vite/src/router/index.js）保持一致；
--   - 权限码 codegen:read / codegen:download 登记到 sys_api_resource（驱动 @PreAuthorize），
--     菜单可见性由 sys_menu.permission_code=codegen:read 驱动；
--   - 全部走 INSERT IGNORE / NOT EXISTS，重复执行结果一致（幂等）。
-- =============================================================

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1040, 0, 0, 'dev-codegen', '代码生成', 'MENU', '/dev/codegen', NULL, 'codegen:read', 'MagicStick', 1, 0, 60, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
  (900000000201, 0, '代码生成（表清单 / 字段元信息 / 生成预览）', 'codegen:read', 'GET', '/api/codegen/tables', 'CodeGenController#tables', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000202, 0, '代码生成（表字段元信息）', 'codegen:read', 'GET', '/api/codegen/tables/{tableName}', 'CodeGenController#tableMeta', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000203, 0, '代码生成（生成预览）', 'codegen:read', 'POST', '/api/codegen/preview', 'CodeGenController#preview', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000204, 0, '代码生成（zip 打包下载）', 'codegen:download', 'POST', '/api/codegen/download', 'CodeGenController#download', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- SUPER_ADMIN 绑定「代码生成」菜单与接口资源
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id = 1040;

INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, id, CURRENT_TIMESTAMP(3) FROM sys_api_resource WHERE id IN (900000000201, 900000000202, 900000000203, 900000000204);
