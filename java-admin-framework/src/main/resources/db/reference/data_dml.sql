-- =====================================================================================
--  data_dml.sql —— 全量数据操作（DML）分类汇总
-- =====================================================================================
--  生成工具：build_reference.py（语句级分类，字符串 / 注释感知）
--  来源文件：db/migration V1__baseline.sql ~ V31__report_menu_restore.sql（31 个）
--            + permission-schema.sql（仓库根，权限模块独立建表脚本）
--  排列顺序：源文件版本序 + 文件内原始顺序；每条语句前带 [源文件 L行号] 来源注释；
--            PREPARE 动态 SQL 块（SET @ddl + PREPARE + EXECUTE + DEALLOCATE）
--            按内部动态语句类型整体归类，保持为一个执行单元。
--  会话语句：各源文件中的 SET NAMES 等会话指令归一为文件头单条。
--
--  ⚠️ 本文件为「分类参考件」，不可直接当作数据库迁移执行！
--     跨版本依赖例证：V6 删除 sys_user_role / sys_user_org 后，V15 又重建两表并
--     从 sys_user.role_id 回填数据，随后 V15 删除 role_id / org_id / primary_org_id 列。
--     若把 DDL 与 DML 拆成两份先后执行，DML 中的回填语句会撞上已被删除的列，
--     直接抛 ERROR 1054 Unknown column 'role_id'，任何新库都建不起来。
--     可执行的全量迁移请使用：db/migration/V32__consolidated_schema.sql（保持原始交错顺序）。
-- =====================================================================================

SET NAMES utf8mb4;



-- =====================================================================================
-- 来源：V1__baseline.sql
-- =====================================================================================


-- [V1__baseline.sql L183-184]
-- 初始超级管理员与角色
INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, sort_no, created_at, updated_at)
VALUES (1, 0, 'SUPER_ADMIN', '超级管理员', 'SYSTEM', 'ACTIVE', 1, NOW(3), NOW(3));

-- [V1__baseline.sql L186-187]
INSERT IGNORE INTO sys_user (id, tenant_id, username, password_hash, display_name, status, token_version, version, created_at, updated_at)
VALUES (1, 0, 'admin', '{bcrypt}$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96wNz/9H1P4F1q0qU3eZ0q7e2m', '超级管理员', 'ACTIVE', 1, 0, NOW(3), NOW(3));

-- [V1__baseline.sql L189-190]
INSERT IGNORE INTO sys_user_role (id, tenant_id, user_id, role_id, scope_org_id, created_at)
VALUES (1, 0, 1, 1, 0, NOW(3));


-- =====================================================================================
-- 来源：V4__fix_admin_password.sql
-- =====================================================================================


-- [V4__fix_admin_password.sql L6-10]
-- V4 修正超级管理员初始口令。
-- 原 V1 中 admin 的 password_hash 为占位 hash（{bcrypt}$2a$10$EixZaYVK...），
-- 该 hash 无对应明文（实测 password/admin/123456/ChangeMe123! 等均不匹配），
-- 导致超级管理员账号无法登录。此处将其修正为明确默认口令 admin123。
-- 仅当口令仍为原占位值时更新，避免覆盖已自行修改的密码。
UPDATE sys_user
SET password_hash = '{bcrypt}$2a$10$HXuNze85xFb7XsvExix/mO/s49oO6K7L7NbFDKGqdXhGa1IsDnvXO',
    password_changed_at = NOW(3)
WHERE username = 'admin'
  AND password_hash = '{bcrypt}$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96wNz/9H1P4F1q0qU3eZ0q7e2m';


-- =====================================================================================
-- 来源：V5__system_modules.sql
-- =====================================================================================


-- [V5__system_modules.sql L64-68]
-- 种子数据：常用字典与系统参数（幂等，重复执行安全）
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, sort_no, remark, created_at, updated_at)
VALUES
    (1, 0, 'sys_normal_disable', '系统开关', 'ACTIVE', 1, '正常 / 停用', NOW(3), NOW(3)),
    (2, 0, 'sys_user_sex', '用户性别', 'ACTIVE', 2, '性别字典', NOW(3), NOW(3)),
    (3, 0, 'sys_yes_no', '是否标识', 'ACTIVE', 3, '是否字典', NOW(3), NOW(3));

-- [V5__system_modules.sql L70-77]
INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_code, dict_label, dict_value, dict_sort, status, remark, created_at, updated_at)
VALUES
    (1, 0, 'sys_normal_disable', '正常', '0', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (2, 0, 'sys_normal_disable', '停用', '1', 2, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (3, 0, 'sys_user_sex', '男', '0', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (4, 0, 'sys_user_sex', '女', '1', 2, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (5, 0, 'sys_yes_no', '是', 'Y', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (6, 0, 'sys_yes_no', '否', 'N', 2, 'ACTIVE', NULL, NOW(3), NOW(3));

-- [V5__system_modules.sql L79-83]
INSERT IGNORE INTO sys_config (id, tenant_id, config_key, config_name, config_value, config_type, remark, status, created_at, updated_at)
VALUES
    (1, 0, 'sys.title', '系统标题', '管理框架', 'STRING', '前端展示标题', 'ACTIVE', NOW(3), NOW(3)),
    (2, 0, 'sys.max.login.fail', '最大登录失败次数', '5', 'INT', '超过则锁定账户', 'ACTIVE', NOW(3), NOW(3)),
    (3, 0, 'sys.captcha.enabled', '是否启用验证码', 'true', 'BOOLEAN', '登录验证码开关', 'ACTIVE', NOW(3), NOW(3));


-- =====================================================================================
-- 来源：V6__user_single_role_org.sql
-- =====================================================================================


-- [V6__user_single_role_org.sql L62-89]
-- 2) 数据回填：多角色时取「数据范围最小(最严格)」的一条，保证降级后不放大权限。
--    优先级：自定义(CUSTOM) > 本部门及子部门(DEPT_AND_CHILD) > 本部门(DEPT/SELF) > 全部(ALL)。
--    仅当该角色在 sys_role_data_scope 上存在对应 resource_code 规则时才参与排序；
--    无规则的视为不限制(ALL)，排在最后。
--    仅当旧关联表 sys_user_role 仍在时才执行；且只填补 role_id 为空的行，避免覆盖既有绑定。
SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'UPDATE sys_user u
         LEFT JOIN (
             SELECT ur.user_id,
                    ur.role_id,
                    ROW_NUMBER() OVER (
                        PARTITION BY ur.user_id
                        ORDER BY FIELD(
                            COALESCE((
                                SELECT MIN(r.scope_type)
                                FROM sys_role_data_scope r
                                WHERE r.role_id = ur.role_id
                                  AND r.resource_code = ''system:user''
                            ), ''ALL''),
                            ''CUSTOM'', ''DEPT_AND_CHILD'', ''DEPT'', ''SELF'', ''ALL''
                        ),
                        ur.role_id
                    ) AS rn
             FROM sys_user_role ur
         ) picked ON picked.user_id = u.id AND picked.rn = 1
         SET u.role_id = picked.role_id
         WHERE u.role_id IS NULL',
        'DO 0')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V6__user_single_role_org.sql L93-111]
-- 3) 部门回填：多部门时取 is_primary 优先，其次取最小 org_id，保证确定性。
--    仅当旧关联表 sys_user_org 仍在时才执行；只填补 org_id 为空的行。
SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'UPDATE sys_user u
         LEFT JOIN (
             SELECT uo.user_id,
                    uo.org_id,
                    ROW_NUMBER() OVER (
                        PARTITION BY uo.user_id
                        ORDER BY uo.is_primary DESC, uo.org_id ASC
                    ) AS rn
             FROM sys_user_org uo
         ) picked ON picked.user_id = u.id AND picked.rn = 1
         SET u.org_id = picked.org_id
         WHERE u.org_id IS NULL',
        'DO 0')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_org'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V6__user_single_role_org.sql L114]
-- 4) 兼容期保留 primary_org_id：与 org_id 保持一致，供尚未切换的旧代码读取。
UPDATE sys_user SET primary_org_id = org_id WHERE org_id IS NOT NULL AND primary_org_id <=> org_id = 0;


-- =====================================================================================
-- 来源：V11__api_resource_seed.sql
-- =====================================================================================


-- [V11__api_resource_seed.sql L22]
-- =============================================================================
-- V11: 接口资源自动登记种子数据 + 超级管理员全量授权
-- =============================================================================
-- 背景：
--   项目共有 61 个带权限码的业务接口，但 sys_api_resource / sys_role_api 长期为空表，
--   导致「角色管理」无法勾选任何接口权限、@PreAuthorize 全部 403。
--   本脚本把扫描器（ApiResourceScanner）从 @PreAuthorize 注解解析出的权限码一次性落库，
--   并把全部接口授权给 SUPER_ADMIN。之后的增量由页面「扫描对比 → 同步」自助完成。
--
-- 数据来源：由 ApiResourceScanner 真实扫描 Spring MVC 路由表导出，非手工编写。
--   已过滤：免鉴权接口（/api/auth/login|refresh）、无权限码接口（/api/profile/** 等
--   走行级校验的个人接口）——这两类不参与权限码授权，落库会污染授权数据。
--
-- 幂等性：
--   全部语句走 INSERT IGNORE / INSERT ... SELECT NOT EXISTS，重复执行结果一致。
--   sys_api_resource 依赖主键幂等，sys_role_api 依赖 (role_id, api_id) 查重。
-- =============================================================================
-- -----------------------------------------------------------------------------
-- 1) 接口资源种子数据
-- -----------------------------------------------------------------------------
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000001, 0, '新建公告（初始为草稿）', 'system:announcement:create', 'POST', '/api/system/announcements', 'AnnouncementController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L23]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000002, 0, '分页查询公告（支持标题/内容关键词搜索）', 'system:announcement:read', 'GET', '/api/system/announcements/page', 'AnnouncementController#page', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L24]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000003, 0, '删除公告（逻辑删除）', 'system:announcement:delete', 'DELETE', '/api/system/announcements/{id}', 'AnnouncementController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L25]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000004, 0, '公告详情（累加浏览次数）', 'system:announcement:read', 'GET', '/api/system/announcements/{id}', 'AnnouncementController#get', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L26]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000005, 0, '编辑公告（仅草稿/已下线可编辑）', 'system:announcement:update', 'PUT', '/api/system/announcements/{id}', 'AnnouncementController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L27]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000006, 0, '下线公告', 'system:announcement:publish', 'POST', '/api/system/announcements/{id}/offline', 'AnnouncementController#offline', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L28]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000007, 0, '发布公告（支持定时生效：publishAt 为空则立即生效）', 'system:announcement:publish', 'POST', '/api/system/announcements/{id}/publish', 'AnnouncementController#publish', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L29]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000008, 0, '切换置顶', 'system:announcement:update', 'POST', '/api/system/announcements/{id}/toggle-top', 'AnnouncementController#toggleTop', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L30]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000009, 0, '接口资源列表', 'system:api:read', 'GET', '/api/system/api-resources', 'ApiResourceController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L31]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000010, 0, '注册接口资源', 'system:api:create', 'POST', '/api/system/api-resources', 'ApiResourceController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L32]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000011, 0, '扫描接口资源并预览差异（不写库）', 'system:api:read', 'GET', '/api/system/api-resources/scan', 'ApiResourceController#scanPreview', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L33]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000012, 0, '执行接口资源扫描同步（写库）', 'system:api:sync', 'POST', '/api/system/api-resources/scan/sync', 'ApiResourceController#sync', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L34]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000013, 0, '删除接口资源', 'system:api:delete', 'DELETE', '/api/system/api-resources/{id}', 'ApiResourceController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L35]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000014, 0, '更新接口资源', 'system:api:update', 'PUT', '/api/system/api-resources/{id}', 'ApiResourceController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L36]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000015, 0, '配置列表', 'system:config:read', 'GET', '/api/system/configs', 'ConfigController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L37]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000016, 0, '创建配置', 'system:config:create', 'POST', '/api/system/configs', 'ConfigController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L38]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000017, 0, '按配置键动态读取', 'system:config:read', 'GET', '/api/system/configs/key/{key}', 'ConfigController#getByKey', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L39]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000018, 0, '删除配置', 'system:config:delete', 'DELETE', '/api/system/configs/{id}', 'ConfigController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L40]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000019, 0, '更新配置', 'system:config:update', 'PUT', '/api/system/configs/{id}', 'ConfigController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L41]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000020, 0, '字典数据列表（按字典类型筛选）', 'system:dict:read', 'GET', '/api/system/dict-data', 'DictDataController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L42]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000021, 0, '创建字典数据', 'system:dict:create', 'POST', '/api/system/dict-data', 'DictDataController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L43]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000022, 0, '删除字典数据', 'system:dict:delete', 'DELETE', '/api/system/dict-data/{id}', 'DictDataController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L44]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000023, 0, '更新字典数据', 'system:dict:update', 'PUT', '/api/system/dict-data/{id}', 'DictDataController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L45]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000024, 0, '字典类型列表', 'system:dict:read', 'GET', '/api/system/dict-types', 'DictTypeController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L46]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000025, 0, '创建字典类型', 'system:dict:create', 'POST', '/api/system/dict-types', 'DictTypeController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L47]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000026, 0, '删除字典类型（存在字典数据时拒绝）', 'system:dict:delete', 'DELETE', '/api/system/dict-types/{id}', 'DictTypeController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L48]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000027, 0, '更新字典类型', 'system:dict:update', 'PUT', '/api/system/dict-types/{id}', 'DictTypeController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L49]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000028, 0, '创建菜单', 'system:menu:create', 'POST', '/api/system/menus', 'MenuController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L50]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000029, 0, '菜单树', 'system:menu:read', 'GET', '/api/system/menus/tree', 'MenuController#tree', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L51]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000030, 0, '删除菜单', 'system:menu:delete', 'DELETE', '/api/system/menus/{id}', 'MenuController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L52]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000031, 0, '更新菜单', 'system:menu:update', 'PUT', '/api/system/menus/{id}', 'MenuController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L53]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000032, 0, '发送站内信（单条指定接收人，或按角色/部门批量发送）', 'system:message:send', 'POST', '/api/system/messages/send', 'MessageController#send', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L54]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000033, 0, '我发出的消息分页（含已读统计）', 'system:message:read', 'GET', '/api/system/messages/sent', 'MessageController#sent', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L55]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000034, 0, '异常日志分页（按时间范围查询关键接口/任务失败记录）', 'system:monitor:read', 'GET', '/api/system/monitor/error-logs', 'MonitorController#errorLogs', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L56]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000035, 0, '异常日志按模块/错误码聚合', 'system:monitor:read', 'GET', '/api/system/monitor/error-summary', 'MonitorController#errorSummary', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L57]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000036, 0, '实时运行指标（CPU/内存/磁盘/JVM）', 'system:monitor:read', 'GET', '/api/system/monitor/metrics', 'MonitorController#metrics', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L58]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000037, 0, '在线会话明细（可按用户名/姓名筛选）', 'system:monitor:read', 'GET', '/api/system/monitor/online-sessions', 'MonitorController#onlineSessions', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L59]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000038, 0, '在线用户与会话汇总', 'system:monitor:read', 'GET', '/api/system/monitor/online-summary', 'MonitorController#onlineSummary', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L60]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000039, 0, '手动触发一次采样（供前端刷新按钮使用）', 'system:monitor:read', 'POST', '/api/system/monitor/sample', 'MonitorController#sample', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L61]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000040, 0, '历史采样趋势（按时间范围）', 'system:monitor:read', 'GET', '/api/system/monitor/samples', 'MonitorController#samples', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L62]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000041, 0, '创建机构', 'system:org:create', 'POST', '/api/system/orgs', 'OrgController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L63]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000042, 0, '机构树', 'system:org:read', 'GET', '/api/system/orgs/tree', 'OrgController#tree', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L64]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000043, 0, '删除机构（存在子机构时拒绝）', 'system:org:delete', 'DELETE', '/api/system/orgs/{id}', 'OrgController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L65]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000044, 0, '更新机构（支持调整上级与层级移动）', 'system:org:update', 'PUT', '/api/system/orgs/{id}', 'OrgController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L66]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000045, 0, '角色列表', 'system:role:read', 'GET', '/api/system/roles', 'RoleController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L67]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000046, 0, '创建角色', 'system:role:create', 'POST', '/api/system/roles', 'RoleController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L68]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000047, 0, '删除角色', 'system:role:delete', 'DELETE', '/api/system/roles/{id}', 'RoleController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L69]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000048, 0, '角色详情（含菜单/接口权限）', 'system:role:read', 'GET', '/api/system/roles/{id}', 'RoleController#get', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L70]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000049, 0, '更新角色', 'system:role:update', 'PUT', '/api/system/roles/{id}', 'RoleController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L71]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000050, 0, '创建用户', 'system:user:create', 'POST', '/api/system/users', 'UserController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L72]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000051, 0, '分页查询用户', 'system:user:read', 'GET', '/api/system/users/page', 'UserController#page', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L73]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000052, 0, '删除用户（物理删除/硬删除）', 'system:user:delete', 'DELETE', '/api/system/users/{id}', 'UserController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L74]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000053, 0, '用户详情', 'system:user:read', 'GET', '/api/system/users/{id}', 'UserController#get', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L75]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000054, 0, '更新用户', 'system:user:update', 'PUT', '/api/system/users/{id}', 'UserController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L76]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000055, 0, '重置密码', 'system:user:update', 'POST', '/api/system/users/{id}/reset-password', 'UserController#resetPassword', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L77]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000056, 0, '我发起的流程', 'workflow:instance:read', 'GET', '/api/workflow/instances/mine', 'InstanceController#mine', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L78]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000057, 0, '发起审批流程', 'workflow:instance:start', 'POST', '/api/workflow/instances/start', 'InstanceController#start', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L79]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000058, 0, '流程审批记录', 'workflow:instance:read', 'GET', '/api/workflow/instances/{processInstanceId}/records', 'InstanceController#records', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L80]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000059, 0, '审批/驳回任务', 'workflow:task:approve', 'POST', '/api/workflow/tasks/complete', 'TaskController#complete', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L81]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000060, 0, '我的待办任务', 'workflow:task:read', 'GET', '/api/workflow/tasks/mine', 'TaskController#mine', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L82]
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000061, 0, '转办任务', 'workflow:task:transfer', 'POST', '/api/workflow/tasks/transfer', 'TaskController#transfer', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V11__api_resource_seed.sql L88-96]
-- -----------------------------------------------------------------------------
-- 2) 超级管理员全量授权（解开「要同步需权限、要有权限得先同步」的死锁）
--    仅对 SUPER_ADMIN 生效；其他角色的授权仍由「角色管理」页面显式勾选。
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, r.id, a.id, CURRENT_TIMESTAMP(3)
FROM sys_role r
         JOIN sys_api_resource a
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1
                  FROM sys_role_api ra
                  WHERE ra.role_id = r.id
                    AND ra.api_id = a.id);


-- =====================================================================================
-- 来源：V12__role_data_scope_api.sql
-- =====================================================================================


-- [V12__role_data_scope_api.sql L22-26]
-- =============================================================================
-- V12: 角色数据权限配置接口资源登记
-- =============================================================================
-- 背景：
--   本轮补齐了角色数据权限的配置入口（RoleController 新增 2 个端点）。
--   V11 已把当时的 61 个接口落库，但这两个端点是 V11 之后新增的，
--   若不登记，SUPER_ADMIN 也无法调用（hasAuthority 找不到对应权限码 -> 403）。
--
-- 权限码复用既有值，不新增：
--   查询 -> system:role:read
--   保存 -> system:role:update
-- 与角色 CRUD 的读写分离保持一致：看数据权限不需要改角色，改数据权限才需要。
--
-- 数据来源：由 ApiResourceScanner 真实扫描 Spring MVC 路由表导出，非手工编写。
--
-- 幂等性：
--   走 INSERT IGNORE（依赖主键幂等），重复执行结果一致。
--   同时补齐 sys_role_api 授权，保证 SUPER_ADMIN 拿到新权限码。
-- =============================================================================
-- 1) 接口资源登记
INSERT IGNORE INTO sys_api_resource
    (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method,
     auth_mode, status, risk_level)
VALUES (900000000062, 0, '角色数据权限列表', 'system:role:read', 'GET',
        '/api/system/roles/{id}/data-scopes', 'RoleController#listDataScopes', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V12__role_data_scope_api.sql L28-32]
INSERT IGNORE INTO sys_api_resource
    (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method,
     auth_mode, status, risk_level)
VALUES (900000000063, 0, '保存角色数据权限（覆盖式）', 'system:role:update', 'PUT',
        '/api/system/roles/{id}/data-scopes', 'RoleController#saveDataScope', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V12__role_data_scope_api.sql L35-44]
-- 2) 补齐 SUPER_ADMIN 授权（V11 的全量授权语句只覆盖当时已存在的资源）
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, r.id, a.id, CURRENT_TIMESTAMP(3)
FROM sys_role r
         JOIN sys_api_resource a
WHERE r.role_code = 'SUPER_ADMIN'
  AND a.id IN (900000000062, 900000000063)
  AND NOT EXISTS (SELECT 1
                  FROM sys_role_api ra
                  WHERE ra.role_id = r.id
                    AND ra.api_id = a.id);


-- =====================================================================================
-- 来源：V14__workflow_definition_resource.sql
-- =====================================================================================


-- [V14__workflow_definition_resource.sql L3-13]
-- V14 自定义工作流定义接口资源与权限码（供 @PreAuthorize("hasAuthority(...)") 与 JWT 鉴权使用）。
-- 全部语句幂等，重复执行结果一致。
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
    (900000000401, 0, '新建工作流草稿', 'workflow:definition:create', 'POST', '/api/workflow/definitions', 'WorkflowDefinitionController#create', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000402, 0, '工作流定义列表', 'workflow:definition:read', 'GET', '/api/workflow/definitions', 'WorkflowDefinitionController#list', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000403, 0, '工作流定义详情', 'workflow:definition:read', 'GET', '/api/workflow/definitions/{id}', 'WorkflowDefinitionController#get', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000404, 0, '保存工作流草稿', 'workflow:definition:update', 'PUT', '/api/workflow/definitions/{id}', 'WorkflowDefinitionController#save', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000405, 0, '删除工作流草稿', 'workflow:definition:delete', 'DELETE', '/api/workflow/definitions/{id}', 'WorkflowDefinitionController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000406, 0, '发布工作流', 'workflow:definition:publish', 'POST', '/api/workflow/definitions/{id}/publish', 'WorkflowDefinitionController#publish', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000407, 0, '取消发布工作流', 'workflow:definition:publish', 'POST', '/api/workflow/definitions/{id}/unpublish', 'WorkflowDefinitionController#unpublish', 'REQUIRED', 'ACTIVE', 'NORMAL'),
    (900000000408, 0, '查看工作流 BPMN', 'workflow:definition:read', 'GET', '/api/workflow/definitions/{id}/bpmn', 'WorkflowDefinitionController#bpmn', 'REQUIRED', 'ACTIVE', 'NORMAL')
ON DUPLICATE KEY UPDATE resource_name = VALUES(resource_name), permission_code = VALUES(permission_code);


-- =====================================================================================
-- 来源：V15__user_role_org_many_to_many.sql
-- =====================================================================================


-- [V15__user_role_org_many_to_many.sql L52-60]
-- 3) 数据回填：把 V6 单列绑定写入关联表（标记 is_primary=1）。
--    仅在 sys_user 仍存在 role_id / org_id 列时执行；INSERT IGNORE 保证重复执行不报错。
SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'INSERT IGNORE INTO sys_user_role (tenant_id, user_id, role_id, is_primary, created_at)
         SELECT 0, id, role_id, 1, NOW(3) FROM sys_user WHERE role_id IS NOT NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V15__user_role_org_many_to_many.sql L62-70]
SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'INSERT IGNORE INTO sys_user_org (tenant_id, user_id, org_id, is_primary, created_at)
         SELECT 0, id, org_id, 1, NOW(3) FROM sys_user WHERE org_id IS NOT NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================================
-- 来源：V17__menu_role_seed.sql
-- =====================================================================================


-- [V17__menu_role_seed.sql L9-26]
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

-- [V17__menu_role_seed.sql L29-30]
-- SUPER_ADMIN 绑定全部种子菜单（tenant_id, role_id, menu_id 为主键）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id BETWEEN 1001 AND 1022;


-- =====================================================================================
-- 来源：V18__workflow_role_seed.sql
-- =====================================================================================


-- [V18__workflow_role_seed.sql L10-12]
-- V18 内置工作流引用角色种子
-- 背景：内置审批流（leaveApproval / expense）的审批节点使用 ROLE=FINANCE（财务角色）作为受让人，
--       运行期 INITIATOR_MANAGER 节点还需 MANAGER 角色解析主管。基线（V1）仅播种 SUPER_ADMIN，
--       缺失二者会导致 BuiltinWorkflowSeeder 在应用启动时发布流程失败：
--       AssigneeResolver.validateConfig 抛 “审批节点 … 引用的角色不存在: FINANCE”，内置工作流无法发布。
-- 此处补齐 FINANCE / MANAGER 角色并绑定超级管理员，使内置流程可发布且运行期可解析出审批人
-- （本地演示直接用 admin 作为审批人；生产环境请按实际组织将角色分配给对应人员）。
-- 幂等：INSERT IGNORE 保证重复执行不报错；sys_role.id 非自增需显式指定，sys_user_role.id 自增省略。
INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, sort_no, created_at, updated_at)
VALUES (2, 0, 'FINANCE', '财务', 'BUSINESS', 'ACTIVE', 2, NOW(3), NOW(3)),
       (3, 0, 'MANAGER', '主管', 'BUSINESS', 'ACTIVE', 3, NOW(3), NOW(3));

-- [V18__workflow_role_seed.sql L15-17]
-- 绑定到超级管理员（user_id=1）；生产环境按需分配给真实财务/主管人员
INSERT IGNORE INTO sys_user_role (tenant_id, user_id, role_id, is_primary, created_at)
VALUES (0, 1, 2, 0, NOW(3)),
       (0, 1, 3, 0, NOW(3));


-- =====================================================================================
-- 来源：V19__usage_report_menu.sql
-- =====================================================================================


-- [V19__usage_report_menu.sql L13-15]
-- =============================================================
-- V19 使用报表菜单与接口资源种子
-- 目的：使「使用报表」作为独立页面出现在侧边栏，并授予 SUPER_ADMIN 访问权限，
--       以支撑运营分析（访问量 / 功能使用频次 / 活跃用户数）。
-- 说明：
--   - 菜单 route_path=/report/usage 与前端路由（pc-admin-vue3-vite/src/router/index.js）保持一致；
--   - 权限码 report:usage:read 同时登记到 sys_menu（驱动侧边栏可见性）与
--     sys_api_resource（驱动 @PreAuthorize 与「接口资源管理」目录），二者分别经
--     sys_role_menu / sys_role_api 绑定到 SUPER_ADMIN(role_id=1)。
--   - 全部走 INSERT IGNORE / NOT EXISTS，重复执行结果一致（幂等）。
-- =============================================================
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1030, 0, 0, 'report-usage', '使用报表', 'MENU', '/report/usage', NULL, 'report:usage:read', 'DataAnalysis', 1, 0, 50, 'ACTIVE', NOW(3), NOW(3));

-- [V19__usage_report_menu.sql L17-19]
INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
  (900000000101, 0, '使用报表（访问量 / 功能使用频次 / 活跃用户数）', 'report:usage:read', 'GET', '/api/workbench/report', 'UsageReportController#report', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V19__usage_report_menu.sql L22-23]
-- SUPER_ADMIN 绑定「使用报表」菜单与接口资源（tenant_id, role_id, menu_id / api_id 为主键或唯一键）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id = 1030;

-- [V19__usage_report_menu.sql L25-26]
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, id, CURRENT_TIMESTAMP(3) FROM sys_api_resource WHERE id = 900000000101;


-- =====================================================================================
-- 来源：V20__report_management_rename_fix.sql
-- =====================================================================================


-- [V20__report_management_rename_fix.sql L14-21]
-- =============================================================
-- V20 报表管理重命名修正（兼容已执行过 V19 的历史库）
-- 目的：在「使用报表 → 报表管理」重命名中，保证已部署数据库的功能逻辑一致，
--       不影响原有数据展示与业务流程。
-- 说明：
--   - V19 已写入的菜单/接口资源行（id 固定为 1030 / 900000000101）通过 UPDATE 统一修正为
--     新标识：菜单名「报表管理」、路由 /report/management、权限码 report:read；
--     接口资源权限码 report:read、路径 /api/report/**、controller ReportController。
--   - 对尚未执行 V19 的全新库：V19 先插入旧值，本脚本随后 UPDATE 为新值（净结果一致），
--     且 UPDATE 为幂等（重复执行结果不变），不会破坏既有数据。
--   - 角色绑定（sys_role_menu / sys_role_api）基于 id 关联，本脚本不改动，保持 SUPER_ADMIN 可见可用。
-- =============================================================
UPDATE sys_menu
SET menu_code      = 'report-management',
    menu_name      = '报表管理',
    route_path     = '/report/management',
    permission_code = 'report:read',
    icon           = 'DataAnalysis',
    updated_at     = NOW(3)
WHERE id = 1030;

-- [V20__report_management_rename_fix.sql L23-29]
UPDATE sys_api_resource
SET resource_name   = '报表管理（使用统计 / 类别统计 / 状态统计）',
    permission_code = 'report:read',
    path_pattern    = '/api/report/**',
    controller_method = 'ReportController',
    updated_at     = NOW(3)
WHERE id = 900000000101;


-- =====================================================================================
-- 来源：V21__job_management.sql
-- =====================================================================================


-- [V21__job_management.sql L75-77]
-- -----------------------------------------------------------------------------
-- 3) 菜单种子：一级菜单「定时任务」(route=/job/list，与前端路由一致)
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1031, 0, 0, 'job-management', '定时任务', 'MENU', '/job/list', NULL, 'job:read', 'Timer', 1, 0, 55, 'ACTIVE', NOW(3), NOW(3));

-- [V21__job_management.sql L83-94]
-- -----------------------------------------------------------------------------
-- 4) 接口资源种子（逐端点登记，与 ApiResourceScanner 扫描结果一致，避免「扫描对比」出现漂移）
--    id 段：900000000102 ~ 900000000111
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
  (900000000102, 0, '定时任务元信息（调度引擎可用性 / 最小间隔）', 'job:read', 'GET', '/api/jobs/meta', 'JobController#meta', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000103, 0, '可选任务处理器列表', 'job:read', 'GET', '/api/jobs/handlers', 'JobController#handlers', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000104, 0, '分页查询定时任务', 'job:read', 'GET', '/api/jobs', 'JobController#page', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000105, 0, '定时任务详情', 'job:read', 'GET', '/api/jobs/{id}', 'JobController#detail', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000106, 0, '定时任务执行日志', 'job:read', 'GET', '/api/jobs/{id}/logs', 'JobController#logs', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000107, 0, '新建定时任务', 'job:manage', 'POST', '/api/jobs', 'JobController#create', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000108, 0, '编辑定时任务', 'job:manage', 'PUT', '/api/jobs/{id}', 'JobController#update', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000109, 0, '启动定时任务', 'job:manage', 'POST', '/api/jobs/{id}/start', 'JobController#start', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000110, 0, '暂停定时任务', 'job:manage', 'POST', '/api/jobs/{id}/pause', 'JobController#pause', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000111, 0, '删除定时任务', 'job:manage', 'DELETE', '/api/jobs/{id}', 'JobController#remove', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V21__job_management.sql L99-102]
-- -----------------------------------------------------------------------------
-- 5) SUPER_ADMIN 绑定菜单与接口资源（与 V17/V19 一致，role_id = 1）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, m.id FROM sys_menu m
WHERE m.id = 1031
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- [V21__job_management.sql L104-107]
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, a.id, CURRENT_TIMESTAMP(3) FROM sys_api_resource a
WHERE a.id BETWEEN 900000000102 AND 900000000111
  AND NOT EXISTS (SELECT 1 FROM sys_role_api ra WHERE ra.role_id = 1 AND ra.api_id = a.id);


-- =====================================================================================
-- 来源：V22__codegen_menu.sql
-- =====================================================================================


-- [V22__codegen_menu.sql L12-14]
-- =============================================================
-- V22 代码生成器菜单与接口资源种子
-- 目的：使「代码生成」作为开发工具页出现在侧边栏，并授予 SUPER_ADMIN 访问权限，
--       支撑按表结构生成 CRUD 骨架（后端六层 + 前端两文件）。
-- 说明：
--   - 菜单 route_path=/dev/codegen 与前端路由（pc-admin-vue3-vite/src/router/index.js）保持一致；
--   - 权限码 codegen:read / codegen:download 登记到 sys_api_resource（驱动 @PreAuthorize），
--     菜单可见性由 sys_menu.permission_code=codegen:read 驱动；
--   - 全部走 INSERT IGNORE / NOT EXISTS，重复执行结果一致（幂等）。
-- =============================================================
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1040, 0, 0, 'dev-codegen', '代码生成', 'MENU', '/dev/codegen', NULL, 'codegen:read', 'MagicStick', 1, 0, 60, 'ACTIVE', NOW(3), NOW(3));

-- [V22__codegen_menu.sql L16-21]
INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
  (900000000201, 0, '代码生成（表清单 / 字段元信息 / 生成预览）', 'codegen:read', 'GET', '/api/codegen/tables', 'CodeGenController#tables', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000202, 0, '代码生成（表字段元信息）', 'codegen:read', 'GET', '/api/codegen/tables/{tableName}', 'CodeGenController#tableMeta', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000203, 0, '代码生成（生成预览）', 'codegen:read', 'POST', '/api/codegen/preview', 'CodeGenController#preview', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000204, 0, '代码生成（zip 打包下载）', 'codegen:download', 'POST', '/api/codegen/download', 'CodeGenController#download', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V22__codegen_menu.sql L24-25]
-- SUPER_ADMIN 绑定「代码生成」菜单与接口资源
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id = 1040;

-- [V22__codegen_menu.sql L27-28]
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, id, CURRENT_TIMESTAMP(3) FROM sys_api_resource WHERE id IN (900000000201, 900000000202, 900000000203, 900000000204);


-- =====================================================================================
-- 来源：V24__report_menu_split.sql
-- =====================================================================================


-- [V24__report_menu_split.sql L17-23]
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

-- [V24__report_menu_split.sql L26-33]
-- 2) 6 个报表子菜单（每个原页签一个独立入口）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1050, 0, 1030, 'report-usage',     '使用统计',         'MENU', '/report/usage',     NULL, 'report:read', 'DataLine',    1, 0, 10, 'ACTIVE', NOW(3), NOW(3)),
  (1051, 0, 1030, 'report-category',  '类别统计',         'MENU', '/report/category',  NULL, 'report:read', 'PieChart',    1, 0, 20, 'ACTIVE', NOW(3), NOW(3)),
  (1052, 0, 1030, 'report-status',    '状态统计',         'MENU', '/report/status',    NULL, 'report:read', 'DataAnalysis',1, 0, 30, 'ACTIVE', NOW(3), NOW(3)),
  (1053, 0, 1030, 'report-job-exec',  '定时任务执行统计', 'MENU', '/report/job-exec',  NULL, 'report:read', 'Timer',       1, 0, 40, 'ACTIVE', NOW(3), NOW(3)),
  (1054, 0, 1030, 'report-login',     '登录统计',         'MENU', '/report/login',     NULL, 'report:read', 'User',        1, 0, 50, 'ACTIVE', NOW(3), NOW(3)),
  (1055, 0, 1030, 'report-api-call',  '接口调用统计',     'MENU', '/report/api-call',  NULL, 'report:read', 'Connection',  1, 0, 60, 'ACTIVE', NOW(3), NOW(3));

-- [V24__report_menu_split.sql L36-37]
-- 3) SUPER_ADMIN 绑定 6 个报表子菜单（父菜单 1030 的既有绑定保留，目录可见性不受影响）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id BETWEEN 1050 AND 1055;


-- =====================================================================================
-- 来源：V25__menu_sort_api.sql
-- =====================================================================================


-- [V25__menu_sort_api.sql L11-12]
-- =============================================================
-- V25 菜单排序接口登记：PUT /api/system/menus/sort
-- 目的：菜单拖拽排序 / 上移下移的批量持久化端点，登记接口资源并绑定 SUPER_ADMIN。
-- 说明：
--   - 权限码复用 system:menu:update（与 PUT /api/system/menus/{id} 同一写权限域），
--     不新增权限码，角色授权模型不变；
--   - id 段 900000000409（接 V21 的 900000000108~111 与 V22/V24 之后的 408）；
--   - INSERT IGNORE 幂等，重复执行结果一致；与 ApiResourceScanner 扫描结果一致，避免漂移。
-- =============================================================
INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES (900000000409, 0, '批量调整菜单排序（拖拽/上下移动）', 'system:menu:update', 'PUT', '/api/system/menus/sort', 'MenuController#reorder', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V25__menu_sort_api.sql L15-18]
-- SUPER_ADMIN 绑定（与 V17/V19/V21/V22 一致，role_id = 1）
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, r.id, CURRENT_TIMESTAMP(3) FROM sys_api_resource r
WHERE r.id = 900000000409
  AND NOT EXISTS (SELECT 1 FROM sys_role_api ra WHERE ra.role_id = 1 AND ra.api_id = r.id);


-- =====================================================================================
-- 来源：V26__org_seed.sql
-- =====================================================================================


-- [V26__org_seed.sql L17-18]
-- =============================================================
-- V26 组织/用户种子：部门总部 + admin 主部门关联 + 测试用户 zhangsan
-- 目的：修复「干净库（CI 从零 Flyway）缺组织种子」导致的链路断裂：
--   1) AssigneeResolver.resolveManager（INITIATOR_MANAGER 受让人）拿发起人主部门，
--      sys_user_org 为空时抛 NOT_FOUND「发起人所在主部门未知」，发起任何含该节点的
--      流程（内置 leaveApproval 的会签节点）即 404；
--   2) 集成测试基线数据缺失（UserIntegrationIT 需部门种子、MessageIntegrationIT 需 ≥2 用户）。
-- 说明：
--   - sys_org/sys_user_org/sys_user 的种子在此前迁移中只有建表（V1）无数据，属既有缺口；
--   - zhangsan 复制 admin 的 password_hash（同库内 SELECT，避免硬编码 BCrypt），
--     仅作测试/演示账号，可正常登录（密码同 admin）；
--   - 全部 INSERT IGNORE（按主键/唯一键幂等），重复执行结果一致；
--   - 已存在组织数据的库（如本机 local 库）不受影响。
-- =============================================================
-- 1) 部门种子：总部（id=1，org_code 租户内唯一）
INSERT IGNORE INTO sys_org (id, tenant_id, parent_id, ancestors, org_code, org_name, org_type, sort_no, status)
VALUES (1, 0, 0, '0', 'HQ', '总部', 'COMPANY', 1, 'ACTIVE');

-- [V26__org_seed.sql L22-23]
-- 2) admin（id=1）主部门关联：使 INITIATOR_MANAGER 解析可拿到部门，
--    且 admin 已绑定 MANAGER/FINANCE 角色（sys_user_role 种子），部门内主管解析非空
INSERT IGNORE INTO sys_user_org (tenant_id, user_id, org_id, is_primary)
VALUES (0, 1, 1, 1);

-- [V26__org_seed.sql L27-28]
-- 3) 测试用户 zhangsan（id=2）：满足「至少 2 个用户」的消息/审批场景；
--    password_hash 复制 admin（同库 SELECT，登录密码与 admin 相同）
INSERT IGNORE INTO sys_user (id, username, password_hash, display_name)
SELECT 2, 'zhangsan', password_hash, '张三' FROM sys_user WHERE id = 1;


-- =====================================================================================
-- 来源：V27__online_user_management.sql
-- =====================================================================================


-- [V27__online_user_management.sql L70-71]
-- -------------------------------------------------------------
-- 菜单：「在线用户管理」挂在「系统管理」(id=1002) 下，接在系统监控(90)之后
-- -------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1060, 0, 1002, 'system:online', '在线用户管理', 'MENU', '/system/online', NULL, 'system:online:read', 'Monitor', 1, 0, 95, 'ACTIVE', NOW(3), NOW(3));

-- [V27__online_user_management.sql L74-76]
-- SUPER_ADMIN 绑定新菜单（与 V17 种子一致，role_id = 1）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1060
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = 1060);

-- [V27__online_user_management.sql L83-89]
-- -------------------------------------------------------------
-- 接口资源种子（与 OnlineUserController 的 @PreAuthorize 一致，
-- 亦与 ApiResourceScanner 扫描结果一致，避免授权漂移）
-- id 段 900000000410~414，接 V25 的 900000000409
-- -------------------------------------------------------------
INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
  (900000000410, 0, '分页查询在线用户会话', 'system:online:read', 'GET', '/api/system/online-users/page', 'OnlineUserController#page', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000411, 0, '分页查询登录限制记录', 'system:online:read', 'GET', '/api/system/online-users/restrictions/page', 'OnlineUserController#restrictionPage', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000412, 0, '强制下线在线会话（按会话批量）', 'system:online:force-logout', 'POST', '/api/system/online-users/force-logout', 'OnlineUserController#forceLogout', 'REQUIRED', 'ACTIVE', 'HIGH'),
  (900000000413, 0, '设置临时限制登录（限时+原因，到期自动解除）', 'system:online:restrict', 'POST', '/api/system/online-users/users/{userId}/restrict', 'OnlineUserController#restrict', 'REQUIRED', 'ACTIVE', 'HIGH'),
  (900000000414, 0, '手动解除登录限制', 'system:online:restrict', 'POST', '/api/system/online-users/users/{userId}/release', 'OnlineUserController#release', 'REQUIRED', 'ACTIVE', 'HIGH');

-- [V27__online_user_management.sql L92-95]
-- SUPER_ADMIN 绑定（与 V17/V19/V21/V22/V24/V25 一致，role_id = 1）
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, r.id, CURRENT_TIMESTAMP(3) FROM sys_api_resource r
WHERE r.id BETWEEN 900000000410 AND 900000000414
  AND NOT EXISTS (SELECT 1 FROM sys_role_api ra WHERE ra.role_id = 1 AND ra.api_id = r.id);


-- =====================================================================================
-- 来源：V28__grant_missing_api_to_super_admin.sql
-- =====================================================================================


-- [V28__grant_missing_api_to_super_admin.sql L20-36]
-- =============================================================================
-- V28: 补授 V11 之后新增接口的 SUPER_ADMIN 授权（D-5 数据修复）
-- =============================================================================
-- 背景：
--   V11 一次性把当时全部接口授予 SUPER_ADMIN，但 ApiResourceService.sync() 历来
--   「只登记资源、不建角色授权」，与 ApiResourceAutoSyncRunner 注释承诺的
--   「新增接口无需手工登记也不会出现 403」相悖。后果：V11 之后上线的端点对
--   超级管理员一律 403，本轮业务测试实测实锤——
--     · system:audit:read            → 审计日志（页面/摘要/链路）超管不可访问
--     · workflow:definition:*        → 工作流设计器（增删改查/发布）超管不可访问
--     · workflow:task:claim          → 任务认领超管不可访问
--
-- 本脚本按权限码把这 7 个缺失授权补授给 SUPER_ADMIN（幂等，可重复执行）。
-- 代码侧同步修复：sync() 对「本次新插入」的资源自动授予 SUPER_ADMIN，
-- 新增接口不再依赖手工迁移补授（仅新插入行授予，人工收回的授权不受影响）。
--
-- 幂等性：INSERT IGNORE + NOT EXISTS 双重去重，重复执行结果一致。
-- =============================================================================
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, r.id, a.id, CURRENT_TIMESTAMP(3)
FROM sys_role r
         JOIN sys_api_resource a ON a.permission_code IN (
    'system:audit:read',
    'workflow:definition:create',
    'workflow:definition:read',
    'workflow:definition:update',
    'workflow:definition:delete',
    'workflow:definition:publish',
    'workflow:task:claim'
    )
WHERE r.role_code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1
                  FROM sys_role_api ra
                  WHERE ra.role_id = r.id
                    AND ra.api_id = a.id);


-- =====================================================================================
-- 来源：V29__monitor_log_menus.sql
-- =====================================================================================


-- [V29__monitor_log_menus.sql L21-26]
-- =============================================================
-- V29 系统管理新增四个子菜单：缓存监控 / 连接池监控 / 登录日志 / 操作日志
-- 目的：
--   1. sys_menu 挂 4 个叶子菜单（父菜单「系统管理」id=1002），sort_no 接 95 之后：
--      缓存监控(100) / 连接池监控(110) / 登录日志(120) / 操作日志(130)，
--      先监控类后日志类，与前端路由 path 顺序一致；
--   2. sys_api_resource 登记本轮新增/复用的接口资源，绑定 SUPER_ADMIN（role_id=1），
--      避免重蹈 V28「新端点从未授权」的覆辙；
--   3. 全部 INSERT IGNORE 幂等，重复执行结果一致。
-- 权限码口径：
--   - 缓存监控 / 连接池监控复用 system:monitor:read（同属监控域，不新增权限码）；
--   - 登录日志新增 system:login-log:read（含用户名/IP/UA，独立授权，最小权限）；
--   - 操作日志复用 system:audit:read（AuditLogController 既有权限）。
-- 图标口径：Element Plus 图标名，与既有菜单风格一致（Box=缓存容器 / Link=连接 /
--   Key=登录凭证 / Document=日志单据），均未被系统管理下现有菜单占用。
-- =============================================================
-- -------------------------------------------------------------
-- 菜单种子（父菜单「系统管理」id=1002，接在线用户管理 95 之后）
-- -------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1061, 0, 1002, 'system:cache', '缓存监控', 'MENU', '/system/cache', NULL, 'system:monitor:read', 'Box', 1, 0, 100, 'ACTIVE', NOW(3), NOW(3)),
  (1062, 0, 1002, 'system:datasource', '连接池监控', 'MENU', '/system/datasource', NULL, 'system:monitor:read', 'Link', 1, 0, 110, 'ACTIVE', NOW(3), NOW(3)),
  (1063, 0, 1002, 'system:login-log', '登录日志', 'MENU', '/system/login-log', NULL, 'system:login-log:read', 'Key', 1, 0, 120, 'ACTIVE', NOW(3), NOW(3)),
  (1064, 0, 1002, 'system:operation-log', '操作日志', 'MENU', '/system/operation-log', NULL, 'system:audit:read', 'Document', 1, 0, 130, 'ACTIVE', NOW(3), NOW(3));

-- [V29__monitor_log_menus.sql L29-33]
-- SUPER_ADMIN 绑定新菜单（与 V17 种子一致，role_id = 1）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, m.id
FROM sys_menu m
WHERE m.id IN (1061, 1062, 1063, 1064)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- [V29__monitor_log_menus.sql L40-46]
-- -------------------------------------------------------------
-- 接口资源种子（与 MonitorController / LoginLogController 的 @PreAuthorize 一致，
-- 亦与 ApiResourceAutoSyncRunner 启动扫描结果一致，避免授权漂移）
-- id 段 900000000415~419，接 V27 的 900000000414
-- -------------------------------------------------------------
INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
  (900000000415, 0, '查询进程内缓存统计', 'system:monitor:read', 'GET', '/api/system/monitor/caches', 'MonitorController#caches', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000416, 0, '清理指定进程内缓存', 'system:monitor:read', 'POST', '/api/system/monitor/caches/{name}/clear', 'MonitorController#clearCache', 'REQUIRED', 'ACTIVE', 'HIGH'),
  (900000000417, 0, '查询数据库连接池统计', 'system:monitor:read', 'GET', '/api/system/monitor/datasource', 'MonitorController#datasource', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000418, 0, '分页查询登录日志', 'system:login-log:read', 'GET', '/api/system/login-logs/page', 'LoginLogController#page', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000419, 0, '登录日志统计摘要', 'system:login-log:read', 'GET', '/api/system/login-logs/summary', 'LoginLogController#summary', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- [V29__monitor_log_menus.sql L49-52]
-- SUPER_ADMIN 绑定（与 V17/V19/V21/V22/V24/V25/V27 一致，role_id = 1）
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, r.id, CURRENT_TIMESTAMP(3) FROM sys_api_resource r
WHERE r.id BETWEEN 900000000415 AND 900000000419
  AND NOT EXISTS (SELECT 1 FROM sys_role_api ra WHERE ra.role_id = 1 AND ra.api_id = r.id);


-- =====================================================================================
-- 来源：V30__ops_menu_restructure.sql
-- =====================================================================================


-- [V30__ops_menu_restructure.sql L21-22]
-- =============================================================
-- V30 菜单重组：移除报表公共统计页，新增「运维管理」一级菜单
-- 目的：
--   1. 移除「报表管理」目录(1030)及 3 个公共统计页（使用 1050 / 类别 1051 / 状态 1052）；
--   2. 新增「运维管理」一级目录(1070)，接手报表管理的排序位 sort_no=50；
--   3. 7 个功能菜单迁入运维管理：接口资源管理(1014) / 代码生成(1040) / 系统监控(1018) /
--      缓存监控(1061) / 连接池监控(1062) / 登录日志(1063) / 操作日志(1064)；
--   4. 保留的 3 个运维向统计页（定时任务执行 1053 / 登录 1054 / 接口调用 1055）
--      一并挂到运维管理下。
-- 权限与业务不变性：
--   - 菜单 id 与权限码均不变 → sys_role_api / sys_api_resource 授权原样保留，
--     接口访问控制零影响；sys_role_menu 按 menu_id 关联，id 不变即授权不丢失；
--   - 前端路由 path 不变（/system/api-resource、/dev/codegen、/report/job-exec 等），
--     侧边栏由后端菜单树驱动，parent 变更后自动归组。
-- 幂等性：删除/更新均带原值 WHERE 条件，INSERT IGNORE + 固定 id，重复执行结果一致。
-- =============================================================
-- -------------------------------------------------------------
-- 1) 新增「运维管理」一级目录（接手 sort_no=50，图标 Tools）
-- -------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1070, 0, 0, 'ops', '运维管理', 'CATALOG', NULL, NULL, NULL, 'Tools', 1, 0, 50, 'ACTIVE', NOW(3), NOW(3));

-- [V30__ops_menu_restructure.sql L25-26]
-- SUPER_ADMIN 绑定新目录（与 V17/V29 种子一致，role_id = 1）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, m.id FROM sys_menu m WHERE m.id = 1070;

-- [V30__ops_menu_restructure.sql L32-33]
-- -------------------------------------------------------------
-- 2) 软删 3 个公共统计页 + 「报表管理」目录容器，并清理角色-菜单关联
--    （MenuService.delete 同类问题：N:N 关联表无外键，须显式清理防孤儿行）
-- -------------------------------------------------------------
UPDATE sys_menu SET deleted = 1, updated_at = NOW(3)
WHERE id IN (1050, 1051, 1052) AND deleted = 0;

-- [V30__ops_menu_restructure.sql L34]
DELETE FROM sys_role_menu WHERE menu_id IN (1050, 1051, 1052);

-- [V30__ops_menu_restructure.sql L36-37]
UPDATE sys_menu SET deleted = 1, updated_at = NOW(3)
WHERE id = 1030 AND deleted = 0;

-- [V30__ops_menu_restructure.sql L38]
DELETE FROM sys_role_menu WHERE menu_id = 1030;

-- [V30__ops_menu_restructure.sql L45-46]
-- -------------------------------------------------------------
-- 3) 7 个功能菜单迁入运维管理，sort_no 按需求顺序重排
--    接口资源管理 10 / 代码生成 20 / 系统监控 30 / 缓存监控 40 /
--    连接池监控 50 / 登录日志 60 / 操作日志 70
-- -------------------------------------------------------------
UPDATE sys_menu SET parent_id = 1070, sort_no = 10, updated_at = NOW(3)
WHERE id = 1014 AND parent_id <> 1070;

-- [V30__ops_menu_restructure.sql L47-48]
UPDATE sys_menu SET parent_id = 1070, sort_no = 20, updated_at = NOW(3)
WHERE id = 1040 AND parent_id <> 1070;

-- [V30__ops_menu_restructure.sql L49-50]
UPDATE sys_menu SET parent_id = 1070, sort_no = 30, updated_at = NOW(3)
WHERE id = 1018 AND parent_id <> 1070;

-- [V30__ops_menu_restructure.sql L51-52]
UPDATE sys_menu SET parent_id = 1070, sort_no = 40, updated_at = NOW(3)
WHERE id = 1061 AND parent_id <> 1070;

-- [V30__ops_menu_restructure.sql L53-54]
UPDATE sys_menu SET parent_id = 1070, sort_no = 50, updated_at = NOW(3)
WHERE id = 1062 AND parent_id <> 1070;

-- [V30__ops_menu_restructure.sql L55-56]
UPDATE sys_menu SET parent_id = 1070, sort_no = 60, updated_at = NOW(3)
WHERE id = 1063 AND parent_id <> 1070;

-- [V30__ops_menu_restructure.sql L57-58]
UPDATE sys_menu SET parent_id = 1070, sort_no = 70, updated_at = NOW(3)
WHERE id = 1064 AND parent_id <> 1070;

-- [V30__ops_menu_restructure.sql L64-65]
-- -------------------------------------------------------------
-- 4) 保留的 3 个运维向统计页挂到运维管理（原父目录 1030 已软删）
--    定时任务执行统计 80 / 登录统计 90 / 接口调用统计 100
-- -------------------------------------------------------------
UPDATE sys_menu SET parent_id = 1070, sort_no = 80, updated_at = NOW(3)
WHERE id = 1053 AND parent_id <> 1070;

-- [V30__ops_menu_restructure.sql L66-67]
UPDATE sys_menu SET parent_id = 1070, sort_no = 90, updated_at = NOW(3)
WHERE id = 1054 AND parent_id <> 1070;

-- [V30__ops_menu_restructure.sql L68-69]
UPDATE sys_menu SET parent_id = 1070, sort_no = 100, updated_at = NOW(3)
WHERE id = 1055 AND parent_id <> 1070;


-- =====================================================================================
-- 来源：V31__report_menu_restore.sql
-- =====================================================================================


-- [V31__report_menu_restore.sql L18-20]
-- =============================================================
-- V31 恢复「报表管理」一级菜单：定时任务执行 / 登录 / 接口调用统计迁入
-- 目的：
--   1. V30 将 3 个运维向统计页（1053/1054/1055）挂到「运维管理」(1070) 后，
--      报表域缺少一级入口；本迁移复活 V30 软删的「报表管理」目录(1030)，
--      作为一级 CATALOG 重新承载这 3 个统计页；
--   2. 3 个统计页菜单 id / 权限码 / route_path 均不变 → sys_role_menu / sys_role_api
--      授权零影响，前端路由零改动，侧边栏由后端菜单树驱动自动归组；
--   3. 「运维管理」(1070) 保留其余 7 个功能菜单，sort_no 不变。
-- 幂等性：复活带 deleted=1 原值 WHERE 条件，INSERT IGNORE + NOT EXISTS，重复执行结果一致。
-- =============================================================
-- -------------------------------------------------------------
-- 1) 复活「报表管理」一级目录（V30 软删，menu_code=report-management）
--    CATALOG 形态由 V24 置好（route_path / permission_code 均为 NULL），
--    此处仅恢复可见性、归位一级，并接排个人中心(40)之后、运维管理(50)之前
-- -------------------------------------------------------------
UPDATE sys_menu
SET deleted = 0, parent_id = 0, sort_no = 45, updated_at = NOW(3)
WHERE id = 1030 AND deleted = 1;

-- [V31__report_menu_restore.sql L23-27]
-- SUPER_ADMIN 重新绑定目录（V30 清理过该绑定，与 V17/V29 种子一致，role_id = 1）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, m.id
FROM sys_menu m
WHERE m.id = 1030
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- [V31__report_menu_restore.sql L34-35]
-- -------------------------------------------------------------
-- 2) 3 个统计页从「运维管理」迁回「报表管理」，sort_no 按 10/20/30 重排
--    定时任务执行统计(1053) / 登录统计(1054) / 接口调用统计(1055)
--    （原 1050/1051/1052 公共统计页保持软删，不复活）
-- -------------------------------------------------------------
UPDATE sys_menu SET parent_id = 1030, sort_no = 10, updated_at = NOW(3)
WHERE id = 1053 AND parent_id <> 1030;

-- [V31__report_menu_restore.sql L36-37]
UPDATE sys_menu SET parent_id = 1030, sort_no = 20, updated_at = NOW(3)
WHERE id = 1054 AND parent_id <> 1030;

-- [V31__report_menu_restore.sql L38-39]
UPDATE sys_menu SET parent_id = 1030, sort_no = 30, updated_at = NOW(3)
WHERE id = 1055 AND parent_id <> 1030;


-- =====================================================================================
-- 来源：permission-schema.sql
-- =====================================================================================


-- [permission-schema.sql L139-161]
-- ============================================================
-- 8. 菜单初始化数据（典型权限管理菜单树）
--    menu_type: 1=目录 2=菜单 3=按钮
--    visible=0 的按钮级菜单不在左侧导航展示，仅用于权限校验
-- ============================================================
INSERT INTO sys_menu (id, parent_id, menu_name, menu_code, menu_type, path, component, icon, permission, sort_no, visible, status) VALUES
-- 顶级：系统管理（目录）
(1, 0,  '系统管理', 'system',        1, '/system',       'Layout',          'setting', '',                1, 1, 1),
-- 用户管理（菜单）
(2, 1,  '用户管理', 'system:user',   2, '/system/user',  'system/user/index', 'user', 'system:user:read',   1, 1, 1),
(3, 2,  '用户新增', 'system:user:create', 3, '', '', '', 'system:user:create', 1, 0, 1),
(4, 2,  '用户编辑', 'system:user:update', 3, '', '', '', 'system:user:update', 2, 0, 1),
(5, 2,  '用户删除', 'system:user:delete', 3, '', '', '', 'system:user:delete', 3, 0, 1),
-- 角色管理（菜单）
(6, 1,  '角色管理', 'system:role',   2, '/system/role',  'system/role/index', 'role', 'system:role:read',   2, 1, 1),
(7, 6,  '角色新增', 'system:role:create', 3, '', '', '', 'system:role:create', 1, 0, 1),
(8, 6,  '角色编辑', 'system:role:update', 3, '', '', '', 'system:role:update', 2, 0, 1),
(9, 6,  '角色删除', 'system:role:delete', 3, '', '', '', 'system:role:delete', 3, 0, 1),
-- 部门管理（菜单）
(10, 1, '部门管理', 'system:dept',   2, '/system/dept',  'system/dept/index', 'tree', 'system:dept:read',   3, 1, 1),
(11, 10, '部门新增', 'system:dept:create', 3, '', '', '', 'system:dept:create', 1, 0, 1),
(12, 10, '部门编辑', 'system:dept:update', 3, '', '', '', 'system:dept:update', 2, 0, 1),
(13, 10, '部门删除', 'system:dept:delete', 3, '', '', '', 'system:dept:delete', 3, 0, 1),
-- 菜单管理（菜单）
(14, 1, '菜单管理', 'system:menu',   2, '/system/menu',  'system/menu/index', 'menu', 'system:menu:read',   4, 1, 1),
(15, 14, '菜单新增', 'system:menu:create', 3, '', '', '', 'system:menu:create', 1, 0, 1),
(16, 14, '菜单编辑', 'system:menu:update', 3, '', '', '', 'system:menu:update', 2, 0, 1),
(17, 14, '菜单删除', 'system:menu:delete', 3, '', '', '', 'system:menu:delete', 3, 0, 1);

-- [permission-schema.sql L171-172]
-- ============================================================
-- 9. 种子数据：超级管理员（SUPER_ADMIN）
--    包含：角色(1) + 用户(1) + 用户-角色关联 + 角色-菜单全量关联
--    说明：username=admin，密码密文为 BCrypt 对 "123456" 的加密结果
--          （Spring Security BCryptPasswordEncoder，可直接登录）
-- ============================================================
-- 9.1 角色：SUPER_ADMIN（内置系统角色，拥有全部菜单权限）
INSERT INTO sys_role (id, role_code, role_name, role_type, description, status, sort_no) VALUES
(1, 'SUPER_ADMIN', '超级管理员', 'SYSTEM', '系统内置超级管理员，拥有全部菜单与操作权限', 1, 1);

-- [permission-schema.sql L175-176]
-- 9.2 用户：超级管理员账号
INSERT INTO sys_user (id, username, password, display_name, email, mobile, status) VALUES
(1, 'admin', '$2a$10$7JB720yubVSZaMOBA1tOJ.9LGGzX1L7j2T/uuI.k/gote1Yq8syi8.', '超级管理员', 'admin@example.com', '13800000000', 1);

-- [permission-schema.sql L179-180]
-- 9.3 用户-角色关联：admin 绑定 SUPER_ADMIN
INSERT INTO sys_user_role (user_id, role_id) VALUES
(1, 1);

-- [permission-schema.sql L183-187]
-- 9.4 角色-菜单关联：SUPER_ADMIN 拥有全部菜单（含目录/菜单/按钮级）
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1, 1), (1, 2), (1, 3), (1, 4), (1, 5),
(1, 6), (1, 7), (1, 8), (1, 9),
(1, 10), (1, 11), (1, 12), (1, 13),
(1, 14), (1, 15), (1, 16), (1, 17);

-- [permission-schema.sql L195-199]
-- ============================================================
-- 10. 部门种子 + 用户-部门（多部门归属）种子
--     演示：一个用户可属于多个部门，并以 is_primary 标记主部门
-- ============================================================
-- 10.1 部门表（树形）：总公司 → 技术部 / 财务部 / 人力资源部
INSERT INTO sys_department (id, parent_id, dept_code, dept_name, ancestors, leader, sort_no, status) VALUES
(1, 0, 'HQ',   '总公司',     '0',      '王总',   1, 1),
(2, 1, 'TECH', '技术部',     '0,1',    '李工',   1, 1),
(3, 1, 'FIN',  '财务部',     '0,1',    '赵财务', 2, 1),
(4, 1, 'HR',   '人力资源部', '0,1',    '钱经理', 3, 1);

-- [permission-schema.sql L202-204]
-- 10.2 用户-部门关联：admin(1) 主部门为技术部，同时兼任财务部
INSERT INTO sys_user_department (user_id, dept_id, is_primary) VALUES
(1, 2, 1),
(1, 3, 0);

