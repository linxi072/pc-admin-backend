-- =============================================================================
-- V21 在线定时任务管理（sys_job 配置 + sys_job_log 日志 + 菜单/接口资源种子）
-- =============================================================================
-- 设计（JobRunr 双层架构）：
--   JobRunr 原生 jobrunr_* 表由框架内部维护、不可 CRUD；因此任务定义元数据自管于 sys_job，
--   执行日志自管于 sys_job_log，调度能力交由 JobRunr。二者通过 scheduler_ref 关联：
--     - CRON / SIMPLE：scheduler_ref = recurring job id（约定 "sysjob-<id>"）；
--     - ONE_TIME     ：scheduler_ref = 一次性 Job 的 UUID 字符串。
--   调度状态：PAUSED（默认，未调度）/ RUNNING（已调度）/ COMPLETED（一次性任务执行完成）。
--
-- 幂等性：建表用 CREATE TABLE IF NOT EXISTS；种子用 INSERT IGNORE / NOT EXISTS，重复执行结果一致。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1) 任务配置表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_job (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    job_name VARCHAR(128) NOT NULL,
    job_group VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    task_key VARCHAR(128) NOT NULL,
    trigger_type VARCHAR(16) NOT NULL,
    cron_expression VARCHAR(128) NULL,
    interval_value INT NULL,
    interval_unit VARCHAR(16) NULL,
    one_time_at DATETIME(3) NULL,
    job_params VARCHAR(2048) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PAUSED',
    scheduler_ref VARCHAR(64) NULL,
    last_fire_time DATETIME(3) NULL,
    next_fire_time DATETIME(3) NULL,
    total_runs BIGINT UNSIGNED NOT NULL DEFAULT 0,
    success_runs BIGINT UNSIGNED NOT NULL DEFAULT 0,
    failure_runs BIGINT UNSIGNED NOT NULL DEFAULT 0,
    last_status VARCHAR(16) NULL,
    last_error VARCHAR(1024) NULL,
    last_duration_ms BIGINT UNSIGNED NULL,
    remark VARCHAR(255) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_sys_job_status (tenant_id, status, deleted),
    KEY idx_sys_job_task_key (task_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 2) 任务执行日志表
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_job_log (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    job_id BIGINT UNSIGNED NOT NULL,
    job_name VARCHAR(128) NULL,
    task_key VARCHAR(128) NULL,
    trigger_type VARCHAR(16) NULL,
    fire_time DATETIME(3) NOT NULL,
    finished_time DATETIME(3) NULL,
    duration_ms BIGINT UNSIGNED NULL,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(1024) NULL,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_sys_job_log_job (tenant_id, job_id, fire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 3) 菜单种子：一级菜单「定时任务」(route=/job/list，与前端路由一致)
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1031, 0, 0, 'job-management', '定时任务', 'MENU', '/job/list', NULL, 'job:read', 'Timer', 1, 0, 55, 'ACTIVE', NOW(3), NOW(3));

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

-- -----------------------------------------------------------------------------
-- 5) SUPER_ADMIN 绑定菜单与接口资源（与 V17/V19 一致，role_id = 1）
-- -----------------------------------------------------------------------------
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, m.id FROM sys_menu m
WHERE m.id = 1031
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, a.id, CURRENT_TIMESTAMP(3) FROM sys_api_resource a
WHERE a.id BETWEEN 900000000102 AND 900000000111
  AND NOT EXISTS (SELECT 1 FROM sys_role_api ra WHERE ra.role_id = 1 AND ra.api_id = a.id);
