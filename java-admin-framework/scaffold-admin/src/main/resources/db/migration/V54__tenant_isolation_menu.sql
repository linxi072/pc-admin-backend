-- V54 租户隔离态势菜单（M28 只读运维诊断页）：把「多租户隔离配置态/运行态/当前作用域/配置校验」诊断页
-- 接入侧边栏与路由守卫。
--
-- 设计要点：
--   1) 后端 GET /api/system/tenant-isolation/status 已在 M28 交付，权限 system:tenant-isolation:read
--      由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，此处仅补前端菜单入口。
--   2) 挂在「系统管理」目录(1002)下，sort_no=114 紧随 租户管理(113) 之后。
--   3) route_path 必须与前端路由（system/tenant-isolation）严格一致，否则路由守卫会重定向到工作台。
--   4) 依赖：V32（系统管理目录 id=1002）、M19 P3（隔离核心 TenantEnforcement/TenantContext）、M28（诊断接口）。
--   5) 幂等：INSERT IGNORE + NOT EXISTS（menu_code 唯一），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1125, 0, 1002, 'system:tenant-isolation', '租户隔离态势', 'MENU', '/system/tenant-isolation', 'system/TenantIsolationView', 'system:tenant-isolation:read', 'Connection', 1, 0, 114, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1125;
