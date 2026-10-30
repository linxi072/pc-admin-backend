-- V55 实时推送管理菜单（M29 只读 + 手动推送运维页）：把后端已交付的 RealtimeController
-- （/api/realtime：sessions / online-count / push/broadcast / push/user）接入侧边栏与路由守卫。
--
-- 设计要点：
--   1) 后端 GET /api/realtime/sessions、/api/realtime/online-count 权限 system:realtime:read；
--      POST /api/realtime/push/broadcast、/api/realtime/push/user 权限 system:realtime:push。
--      权限码由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，
--      此处仅补前端菜单入口（基础 read 权限），与 MonitorView 用 system:monitor:read 覆盖多子权限一致。
--   2) 挂在「系统管理」目录(1002)下，sort_no=115 紧随 租户隔离态势(1125 / 114) 之后。
--   3) route_path 必须与前端路由（system/realtime）严格一致，否则路由守卫会重定向到工作台。
--   4) 依赖：V32（系统管理目录 id=1002）、realtime 模块 RealtimeController（已交付）。
--   5) 幂等：INSERT IGNORE + NOT EXISTS（menu_code 唯一），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1126, 0, 1002, 'system:realtime', '实时推送', 'MENU', '/system/realtime', 'system/RealtimeView', 'system:realtime:read', 'Bell', 1, 0, 115, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （push 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1126;
