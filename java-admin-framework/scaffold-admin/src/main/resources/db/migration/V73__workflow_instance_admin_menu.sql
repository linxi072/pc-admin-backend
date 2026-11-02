-- V73 流程实例治理菜单（M23 续 /api/workflow/admin）：
-- 为运行实例的挂起 / 恢复 / 改派 / 迁移四个治理端点提供前端入口「流程实例治理」。
-- 设计要点：
--   1) 挂载「工作流」目录（id=2006，V67 重组后的 CATALOG），sort_no=50 紧随流程监控(40) 之后；
--   2) route_path 须与前端路由（workflow/instance-admin）严格一致，否则路由守卫会重定向到工作台；
--   3) 权限码 workflow:instance:reassign 作为菜单可见性判定码；workflow:instance:suspend / :migrate
--      由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，无需手工登记；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1144, 0, 2006, 'workflow:instance-admin', '流程实例治理', 'MENU', '/workflow/instance-admin', 'workflow/InstanceAdminView', 'workflow:instance:reassign', 'SetUp', 1, 0, 50, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1144;
