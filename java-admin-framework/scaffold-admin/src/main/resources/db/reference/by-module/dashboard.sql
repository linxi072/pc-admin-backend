-- ============================================================
-- 模块参考 SQL：报表大屏
-- 说明：大屏菜单播种
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V52__dashboard_menu.sql
-- ============================================================

-- V52 报表大屏菜单（M27 前端配套页）：把「运营总览 + 时间序列趋势」页接入侧边栏与路由守卫。
--
-- 设计要点：
--   1) 后端 /api/system/dashboard/overview 与 /trend 已在 M27 四刀交付，权限 system:dashboard:read
--      由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，此处仅补前端菜单入口。
--   2) 挂在「报表管理」目录(1030)下，sort_no=40 紧随 定时任务执行(10)/登录(20)/接口调用(30) 之后。
--   3) route_path 必须与前端路由（report/dashboard）严格一致，否则路由守卫会重定向到工作台。
--   4) 依赖：V32（报表管理目录 id=1030）、M27（dashboard 接口）。幂等：INSERT IGNORE + NOT EXISTS。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1056, 0, 1030, 'system:dashboard', '报表大屏', 'MENU', '/report/dashboard', 'report/DashboardView', 'system:dashboard:read', 'DataAnalysis', 1, 0, 40, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1056;

