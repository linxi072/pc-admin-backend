-- =============================================================
-- V27 在线用户管理（系统管理模块）
-- 目的：
--   1. sys_online_session  在线会话跟踪表：登录即登记、心跳保活、登出/强制下线置离线，
--      支撑「在线用户列表 / 最近活跃时间 / 会话状态 / 强制下线后实时失效」；
--   2. sys_login_restriction 临时登录限制表：限制时长 + 原因 + 到期自动解除 / 手动解除，
--      登录链路强制校验，限制生效时同步踢下线；
--   3. 菜单「在线用户管理」与 5 个接口资源种子，绑定 SUPER_ADMIN（role_id=1）。
-- 说明：
--   - 在线会话以 (tenant_id, user_id, device_id) 为业务键：device_id 来自前端
--     X-Device-Id 头（见 pc-admin-vue3-vite/src/api/http.js），与刷新令牌的
--     device_id 同源，强制下线按该键吊销刷新令牌并置会话离线；
--   - 全部 INSERT IGNORE 幂等，重复执行结果一致。
-- =============================================================

CREATE TABLE IF NOT EXISTS sys_online_session (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    device_id VARCHAR(128) NULL,
    client_id VARCHAR(64) NOT NULL DEFAULT 'web',
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NULL,
    login_ip VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    login_at DATETIME(3) NOT NULL,
    last_active_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ONLINE',
    offline_reason VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 同一用户同一设备仅一行在线会话：重复登录（同设备） upsert 而非插多行
    UNIQUE KEY uk_user_device (tenant_id, user_id, device_id),
    KEY idx_status_active (tenant_id, status, last_active_at),
    KEY idx_username (tenant_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_login_restriction (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    username VARCHAR(64) NOT NULL,
    display_name VARCHAR(100) NULL,
    reason VARCHAR(255) NOT NULL,
    -- 限制时长（分钟）；0 表示长期限制，仅可手动解除
    duration_minutes INT NOT NULL DEFAULT 0,
    restricted_by BIGINT UNSIGNED NULL,
    restricted_by_name VARCHAR(100) NULL,
    restricted_at DATETIME(3) NOT NULL,
    -- 到期时间；duration_minutes=0 时为 NULL（永久）
    expire_at DATETIME(3) NULL,
    released_at DATETIME(3) NULL,
    released_by BIGINT UNSIGNED NULL,
    -- 解除方式：MANUAL=人工解除 / AUTO_EXPIRED=到期自动解除
    release_type VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_status (tenant_id, user_id, status),
    -- 到期自动解除扫描：按状态 + 到期时间定位待释放记录
    KEY idx_status_expire (tenant_id, status, expire_at),
    KEY idx_username (tenant_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------
-- 菜单：「在线用户管理」挂在「系统管理」(id=1002) 下，接在系统监控(90)之后
-- -------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1060, 0, 1002, 'system:online', '在线用户管理', 'MENU', '/system/online', NULL, 'system:online:read', 'Monitor', 1, 0, 95, 'ACTIVE', NOW(3), NOW(3));

-- SUPER_ADMIN 绑定新菜单（与 V17 种子一致，role_id = 1）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1060
WHERE NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = 1060);

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

-- SUPER_ADMIN 绑定（与 V17/V19/V21/V22/V24/V25 一致，role_id = 1）
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, r.id, CURRENT_TIMESTAMP(3) FROM sys_api_resource r
WHERE r.id BETWEEN 900000000410 AND 900000000414
  AND NOT EXISTS (SELECT 1 FROM sys_role_api ra WHERE ra.role_id = 1 AND ra.api_id = r.id);
