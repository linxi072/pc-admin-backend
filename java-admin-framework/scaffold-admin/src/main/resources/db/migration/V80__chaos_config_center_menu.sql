-- V80 混沌工程（R15）与配置中心（R16）前端可视化页菜单种子。
-- 设计要点：
--   1) 标识符全部显式加引号保留 snake_case 小写（对齐 V63/V77 约定）；
--   2) 两个控制器均受 @ConditionalOnProperty 特征开关守卫
--      （app.chaos.enabled / app.config-center.enabled），未启用时控制器不注册；
--      权限码由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动登记并授予 SUPER_ADMIN，
--      菜单种子只需声明 menu_code 与 permission_code 的对应关系；
--   3) 二者均挂在「系统管理」(parent_id=1002) 下；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

-- 混沌工程入口
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1150, 0, 1002, 'system:chaos', '混沌工程', 'MENU', '/system/chaos', 'system/ChaosView', 'system:chaos:read', 'Warning', 1, 0, 124, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 1150;

-- 配置中心入口
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1151, 0, 1002, 'system:config-center', '配置中心', 'MENU', '/system/config-center', 'system/ConfigCenterView', 'system:config-center:read', 'Setting', 1, 0, 125, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 1151;
