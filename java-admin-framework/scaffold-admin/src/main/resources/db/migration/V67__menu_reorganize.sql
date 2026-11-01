-- =============================================================
-- V67 菜单体系按业务域重新分类
-- 目的：将原先堆砌在「系统管理」下的 40+ 功能菜单，按实际业务域归并为
--       13 个一级分类（目录），提升侧边栏可读性与权限分配清晰度。
-- 设计来源：与用户确认的新菜单体系（一级=主分类，二级=子功能）。
-- 幂等：全量 INSERT IGNORE + UPDATE ... WHERE menu_code/id，可重复执行。
-- 说明：
--   - 新目录使用独立 ID 段 2001-2013，避开现有种子（最大 1132）防撞；
--   - 旧目录（系统管理 1002 / 工作流 1003 / 报表管理 1030 / 运维管理 1070）子项清空后隐藏（visible=0）；
--   - 新目录均绑定 SUPER_ADMIN(role_id=1, tenant_id=0)；
--   - 二级功能按 menu_code 重挂，原有 sys_role_menu 绑定（按 menu_id）保持不变，自动随目录上树；
--   - 报表统计页 1053/1054/1055 按 id 处理：menu_code 'report-usage' 在 1030 与已软删的 1050 间重复，
--     故报表域一律以 id 定位，避免误伤软删行或把目录本身当叶子重挂。
-- =============================================================

-- 1) 新增 13 个一级目录（parent_id=0, CATALOG）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (2001, 0, 0, 'cat:workbench',    '工作台',     'CATALOG', NULL, NULL, NULL, 'Odometer',     1, 0, 10,  'ACTIVE', NOW(3), NOW(3)),
  (2002, 0, 0, 'cat:personal',     '个人中心',   'CATALOG', NULL, NULL, NULL, 'User',         1, 0, 20,  'ACTIVE', NOW(3), NOW(3)),
  (2003, 0, 0, 'cat:org-system',   '组织与系统', 'CATALOG', NULL, NULL, NULL, 'Setting',      1, 0, 30,  'ACTIVE', NOW(3), NOW(3)),
  (2004, 0, 0, 'cat:security',     '权限与安全', 'CATALOG', NULL, NULL, NULL, 'Lock',         1, 0, 40,  'ACTIVE', NOW(3), NOW(3)),
  (2005, 0, 0, 'cat:notify',       '消息与通知', 'CATALOG', NULL, NULL, NULL, 'Bell',         1, 0, 50,  'ACTIVE', NOW(3), NOW(3)),
  (2006, 0, 0, 'cat:workflow',     '工作流',     'CATALOG', NULL, NULL, NULL, 'Share',        1, 0, 60,  'ACTIVE', NOW(3), NOW(3)),
  (2007, 0, 0, 'cat:form',         '表单中心',   'CATALOG', NULL, NULL, NULL, 'Document',     1, 0, 70,  'ACTIVE', NOW(3), NOW(3)),
  (2008, 0, 0, 'cat:ops',          '运维与监控', 'CATALOG', NULL, NULL, NULL, 'Monitor',      1, 0, 80,  'ACTIVE', NOW(3), NOW(3)),
  (2009, 0, 0, 'cat:tenant',       '租户管理',   'CATALOG', NULL, NULL, NULL, 'OfficeBuilding',1, 0, 90,  'ACTIVE', NOW(3), NOW(3)),
  (2010, 0, 0, 'cat:data-exchange','数据交换',   'CATALOG', NULL, NULL, NULL, 'Connection',   1, 0, 100, 'ACTIVE', NOW(3), NOW(3)),
  (2011, 0, 0, 'cat:open-platform','开放平台',   'CATALOG', NULL, NULL, NULL, 'Key',          1, 0, 110, 'ACTIVE', NOW(3), NOW(3)),
  (2012, 0, 0, 'cat:report',       '报表分析',   'CATALOG', NULL, NULL, NULL, 'DataAnalysis', 1, 0, 120, 'ACTIVE', NOW(3), NOW(3)),
  (2013, 0, 0, 'cat:dev-tools',    '开发者工具', 'CATALOG', NULL, NULL, NULL, 'MagicStick',   1, 0, 130, 'ACTIVE', NOW(3), NOW(3));

-- 2) 按业务域重新挂载二级功能（按 menu_code 定位，幂等可重复执行）
UPDATE sys_menu SET parent_id = 2001, updated_at = NOW(3) WHERE menu_code IN ('dashboard');
UPDATE sys_menu SET parent_id = 2002, updated_at = NOW(3) WHERE menu_code IN ('profile');
UPDATE sys_menu SET parent_id = 2003, updated_at = NOW(3) WHERE menu_code IN ('system:user', 'system:role', 'system:department', 'system:menu', 'system:dict', 'system:config', 'system:post');
UPDATE sys_menu SET parent_id = 2004, updated_at = NOW(3) WHERE menu_code IN ('system:api-resource', 'system:data-scope', 'system:permission-policy', 'system:mask-strategy', 'system:oauth2-token');
UPDATE sys_menu SET parent_id = 2005, updated_at = NOW(3) WHERE menu_code IN ('system:notice', 'system:notification-template', 'system:notification-record', 'system:sms-record', 'system:realtime');
UPDATE sys_menu SET parent_id = 2006, updated_at = NOW(3) WHERE menu_code IN ('workflow:task', 'workflow:instance', 'workflow:definition', 'workflow:monitor');
UPDATE sys_menu SET parent_id = 2007, updated_at = NOW(3) WHERE menu_code IN ('system:form-definition', 'system:form-submission', 'system:form-pilot');
UPDATE sys_menu SET parent_id = 2008, updated_at = NOW(3) WHERE menu_code IN ('system:monitor', 'system:online', 'system:cache', 'system:datasource', 'system:observability-center', 'system:health', 'system:login-log', 'system:operation-log');
UPDATE sys_menu SET parent_id = 2009, updated_at = NOW(3) WHERE menu_code IN ('system:tenant', 'system:tenant-isolation');
UPDATE sys_menu SET parent_id = 2010, updated_at = NOW(3) WHERE menu_code IN ('system:import', 'system:export', 'file-management');
UPDATE sys_menu SET parent_id = 2011, updated_at = NOW(3) WHERE menu_code IN ('system:openapi-client');
UPDATE sys_menu SET parent_id = 2013, updated_at = NOW(3) WHERE menu_code IN ('job-management', 'dev-codegen');

-- 2b) 报表分析域：3 个活跃统计页按 id 归入新目录（避开重复的 menu_code 'report-usage'）；报表大屏归入工作台
UPDATE sys_menu SET parent_id = 2012, updated_at = NOW(3) WHERE id IN (1053, 1054, 1055);
UPDATE sys_menu SET parent_id = 2001, updated_at = NOW(3) WHERE menu_code = 'system:dashboard';

-- 3) 隐藏已清空的旧目录（系统管理 / 工作流 / 报表管理 / 运维管理），避免侧边栏出现空分组
UPDATE sys_menu SET visible = 0, updated_at = NOW(3) WHERE id IN (1002, 1003, 1030, 1070) AND menu_type = 'CATALOG';

-- 4) 新目录绑定 SUPER_ADMIN，保证超管侧边栏可见（tenant_id, role_id, menu_id 为主键，INSERT IGNORE 幂等）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
VALUES
  (0, 1, 2001), (0, 1, 2002), (0, 1, 2003), (0, 1, 2004), (0, 1, 2005),
  (0, 1, 2006), (0, 1, 2007), (0, 1, 2008), (0, 1, 2009), (0, 1, 2010),
  (0, 1, 2011), (0, 1, 2012), (0, 1, 2013);
