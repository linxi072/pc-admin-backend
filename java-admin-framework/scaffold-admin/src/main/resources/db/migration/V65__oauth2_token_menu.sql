-- M43 OAuth2 令牌管理菜单：在「系统管理」(1002) 下新增「OAuth2 令牌管理」入口，
-- 承载活动令牌列表查看与吊销（M42 健康探针之后，sort_no=120）。
-- 设计要点：
--   1) 后端控制器 OAuth2TokenController（/api/system/oauth2-tokens），读权限 system:oauth2-token:read、
--      吊销权限 system:oauth2-token:revoke，均经 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN；
--   2) route_path 须与前端路由（system/oauth2-tokens）严格一致，否则路由守卫会重定向到工作台；
--   3) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN，可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1131, 0, 1002, 'system:oauth2-token', 'OAuth2 令牌管理', 'MENU', '/system/oauth2-tokens', 'system/TokenManagerView', 'system:oauth2-token:read', 'Key', 1, 0, 120, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1131;
