-- V77 AI 辅助开发（ROADMAP R3）：新增 scaffold-ai 模块 + AI 脚手架菜单种子。
-- 设计要点：
--   1) 标识符全部显式加引号保留 snake_case 小写（对齐 V74/V76 约定）；
--   2) AI 脚手架为只读生成入口，统一权限码 ai:scaffold:generate，
--      由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动登记并授予 SUPER_ADMIN；
--   3) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9601, 0, 0, 'ai', 'AI 辅助开发', 'CATALOG', NULL, NULL, NULL, 'Cpu', 1, 0, 96, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9602, 0, 9601, 'ai:scaffold', '代码生成助手', 'MENU', '/ai/scaffold', 'ai/ScaffoldView', 'ai:scaffold:generate', 'MagicStick', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9602;
