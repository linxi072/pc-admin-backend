-- =============================================================
-- V24 报表管理页签拆分：每个报表视角独立为子菜单
-- 目的：原「报表管理」单页内 6 个报表类型 Tab（使用/类别/状态/定时任务执行/登录/接口调用）
--       拆分为 6 个独立功能菜单项，父菜单「报表管理」目录化（CATALOG）承载层级，
--       各子菜单 route_path 与前端路由（pc-admin-vue3-vite/src/router/index.js）保持一致。
-- 说明：
--   - 父菜单 id=1030（V19 建、V20 改名）由叶子 MENU 升级为 CATALOG：route_path / permission_code 置空，
--     分组本身不导航、不鉴权（与 V17 系统管理/工作流目录范式一致）；
--   - 6 个子菜单 id=1050~1055（避开 V21 已占用的 1031），permission_code 统一为 report:read——
--     报表域权限模型不变（接口 /api/report/** 仍由 report:read 鉴权），菜单可见性走 sys_role_menu 独立绑定，
--     角色可在「角色管理」中按需绑定单个报表菜单而无需改动接口权限；
--   - 旧路由 /report/management 在前端保留重定向至 /report/usage，历史书签/外部链接不失效；
--   - 全部走 UPDATE / INSERT IGNORE，重复执行结果一致（幂等）。
-- =============================================================

-- 1) 父菜单目录化：报表管理（叶子页 → 分组目录）
UPDATE sys_menu
SET menu_type      = 'CATALOG',
    route_path     = NULL,
    component_path = NULL,
    permission_code = NULL,
    updated_at     = NOW(3)
WHERE id = 1030;

-- 2) 6 个报表子菜单（每个原页签一个独立入口）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1050, 0, 1030, 'report-usage',     '使用统计',         'MENU', '/report/usage',     NULL, 'report:read', 'DataLine',    1, 0, 10, 'ACTIVE', NOW(3), NOW(3)),
  (1051, 0, 1030, 'report-category',  '类别统计',         'MENU', '/report/category',  NULL, 'report:read', 'PieChart',    1, 0, 20, 'ACTIVE', NOW(3), NOW(3)),
  (1052, 0, 1030, 'report-status',    '状态统计',         'MENU', '/report/status',    NULL, 'report:read', 'DataAnalysis',1, 0, 30, 'ACTIVE', NOW(3), NOW(3)),
  (1053, 0, 1030, 'report-job-exec',  '定时任务执行统计', 'MENU', '/report/job-exec',  NULL, 'report:read', 'Timer',       1, 0, 40, 'ACTIVE', NOW(3), NOW(3)),
  (1054, 0, 1030, 'report-login',     '登录统计',         'MENU', '/report/login',     NULL, 'report:read', 'User',        1, 0, 50, 'ACTIVE', NOW(3), NOW(3)),
  (1055, 0, 1030, 'report-api-call',  '接口调用统计',     'MENU', '/report/api-call',  NULL, 'report:read', 'Connection',  1, 0, 60, 'ACTIVE', NOW(3), NOW(3));

-- 3) SUPER_ADMIN 绑定 6 个报表子菜单（父菜单 1030 的既有绑定保留，目录可见性不受影响）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id BETWEEN 1050 AND 1055;
