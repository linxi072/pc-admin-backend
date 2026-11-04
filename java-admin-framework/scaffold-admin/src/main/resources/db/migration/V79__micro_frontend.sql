-- V79 微前端宿主（ROADMAP R12）：子应用挂载菜单种子。
-- 设计要点：
--   1) R12 采用离线安全的自研主壳方案（Vite import.meta.glob 懒加载 micro-apps/*/index.js 的
--      mount/unmount 契约），不引入 @originjs/vite-plugin-federation 外部依赖；
--   2) 宿主为纯前端页面（无新后端端点），permission_code 复用既有已登记的
--      system:monitor:read（与「系统监控」「健康探针」同权，参照 V42 health 菜单先例），
--      保证 SUPER_ADMIN 及持权角色可见；
--   3) 子应用清单由前端 src/micro-apps/*/index.js 目录结构驱动，新增子应用无需后端变更；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9801, 0, 0, 'micro', '微前端', 'CATALOG', NULL, NULL, NULL, 'MagicStick', 1, 0, 92, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9802, 0, 9801, 'micro:host', '微前端宿主', 'MENU', '/micro/host', 'micro/MicroFrontendHost', 'system:monitor:read', 'Menu', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9802;
