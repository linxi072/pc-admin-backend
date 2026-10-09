-- =============================================================
-- V17 菜单与角色-菜单绑定种子
-- 目的：使「按角色动态加载菜单」开箱即可演示——
--       SUPER_ADMIN(role_id=1) 绑定全部菜单，admin 登录后即可看到动态菜单树。
-- 说明：菜单 route_path 与前端路由（pc-admin-vue3-vite/src/router/index.js）保持一致；
--       菜单数据仅作为角色配置的初始值，后续可在「菜单管理 / 角色管理」界面灵活调整，前端不硬编码。
-- =============================================================

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1001, 0, 0, 'dashboard',       '工作台',     'MENU',   '/dashboard',         NULL, NULL,                          'Odometer',     1, 0, 10, 'ACTIVE', NOW(3), NOW(3)),
  (1002, 0, 0, 'system',         '系统管理',   'CATALOG', NULL,                NULL, NULL,                          'Setting',      1, 0, 20, 'ACTIVE', NOW(3), NOW(3)),
  (1003, 0, 0, 'workflow',       '工作流',     'CATALOG', NULL,                NULL, NULL,                          'Share',        1, 0, 30, 'ACTIVE', NOW(3), NOW(3)),
  (1004, 0, 0, 'profile',        '个人中心',   'MENU',   '/profile',           NULL, NULL,                          'User',         1, 0, 40, 'ACTIVE', NOW(3), NOW(3)),
  (1010, 0, 1002, 'system:user',        '用户管理',       'MENU', '/system/user',        NULL, 'system:user:read',        'User',          1, 0, 10, 'ACTIVE', NOW(3), NOW(3)),
  (1011, 0, 1002, 'system:role',        '角色管理',       'MENU', '/system/role',        NULL, 'system:role:read',        'Avatar',        1, 0, 20, 'ACTIVE', NOW(3), NOW(3)),
  (1012, 0, 1002, 'system:menu',        '菜单管理',       'MENU', '/system/menu',        NULL, 'system:menu:read',        'Menu',          1, 0, 30, 'ACTIVE', NOW(3), NOW(3)),
  (1013, 0, 1002, 'system:department',  '部门管理',       'MENU', '/system/department',  NULL, 'system:department:read',  'OfficeBuilding',1, 0, 40, 'ACTIVE', NOW(3), NOW(3)),
  (1014, 0, 1002, 'system:api-resource','接口资源管理',   'MENU', '/system/api-resource', NULL, 'system:api-resource:read','Connection',    1, 0, 50, 'ACTIVE', NOW(3), NOW(3)),
  (1015, 0, 1002, 'system:dict',        '字典管理',       'MENU', '/system/dict',        NULL, 'system:dict:read',        'Notebook',      1, 0, 60, 'ACTIVE', NOW(3), NOW(3)),
  (1016, 0, 1002, 'system:config',      '系统变量',       'MENU', '/system/config',      NULL, 'system:config:read',      'Coin',          1, 0, 70, 'ACTIVE', NOW(3), NOW(3)),
  (1017, 0, 1002, 'system:notice',      '消息中心',       'MENU', '/system/notice',      NULL, 'system:announcement:read','Bell',          1, 0, 80, 'ACTIVE', NOW(3), NOW(3)),
  (1018, 0, 1002, 'system:monitor',     '系统监控',       'MENU', '/system/monitor',     NULL, 'system:monitor:read',     'Odometer',      1, 0, 90, 'ACTIVE', NOW(3), NOW(3)),
  (1020, 0, 1003, 'workflow:task',      '我的待办',       'MENU', '/workflow/task',      NULL, 'workflow:task:read',      'Tickets',       1, 0, 10, 'ACTIVE', NOW(3), NOW(3)),
  (1021, 0, 1003, 'workflow:instance',  '我发起的流程',   'MENU', '/workflow/instance',  NULL, 'workflow:instance:read',  'Document',      1, 0, 20, 'ACTIVE', NOW(3), NOW(3)),
  (1022, 0, 1003, 'workflow:definition', '工作流设计',     'MENU', '/workflow/definition',NULL, 'workflow:definition:read','Edit',          1, 0, 30, 'ACTIVE', NOW(3), NOW(3));

-- SUPER_ADMIN 绑定全部种子菜单（tenant_id, role_id, menu_id 为主键）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id BETWEEN 1001 AND 1022;
