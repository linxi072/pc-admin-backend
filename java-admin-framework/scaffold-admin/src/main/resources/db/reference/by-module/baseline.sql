-- ============================================================
-- 模块参考 SQL：基础框架（V1~V31 合并）
-- 说明：用户/角色/菜单/部门/岗位/字典/参数/审计/登录日志/权限/接口资源/工作流基线等核心表与种子
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V32__consolidated_schema.sql
-- ============================================================

-- =====================================================================================
--  V32__consolidated_schema：全量结构合并迁移（consolidated schema）
-- =====================================================================================
--  背景：原 V1 ~ V31 共 31 个版本化迁移按「类型分类 + 原件删除」策略重整，
--       本文件为其逐版本原样合并（语句级保持原始执行顺序），供 Flyway 在
--       全新环境一次性建库；既有环境请勿重复执行（见 README §34 的标记步骤）。
--  内容：以下各「来源迁移」块均为原始文件完整拷贝（含原始注释），未改一字。
--  顺序：V1__baseline.sql -> V31__report_menu_restore.sql（版本升序）。
-- =====================================================================================


-- =====================================================================================
-- 来源迁移：V1__baseline.sql（原样保留，含原始注释）
-- =====================================================================================

-- V1 基线：RBAC 核心表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    mobile VARCHAR(32) NULL,
    email VARCHAR(128) NULL,
    primary_org_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_count INT UNSIGNED NOT NULL DEFAULT 0,
    locked_until DATETIME(3) NULL,
    password_changed_at DATETIME(3) NULL,
    password_expired TINYINT UNSIGNED NOT NULL DEFAULT 0,
    token_version INT UNSIGNED NOT NULL DEFAULT 1,
    mfa_enabled TINYINT UNSIGNED NOT NULL DEFAULT 0,
    last_login_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    UNIQUE KEY uk_tenant_mobile (tenant_id, mobile),
    KEY idx_org_status (tenant_id, primary_org_id, status),
    KEY idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_org (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ancestors VARCHAR(1000) NOT NULL DEFAULT '0',
    org_code VARCHAR(64) NOT NULL,
    org_name VARCHAR(128) NOT NULL,
    org_type VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT',
    sort_no INT NOT NULL DEFAULT 0,
    leader_user_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_org_code (tenant_id, org_code),
    KEY idx_parent (tenant_id, parent_id, sort_no),
    KEY idx_ancestors (tenant_id, ancestors(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_user_org (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    org_id BIGINT UNSIGNED NOT NULL,
    is_primary TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_org (tenant_id, user_id, org_id),
    KEY idx_org_user (tenant_id, org_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_code VARCHAR(64) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    role_type VARCHAR(20) NOT NULL DEFAULT 'BUSINESS',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_no INT NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_role_code (tenant_id, role_code),
    KEY idx_status_sort (tenant_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    scope_org_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    valid_from DATETIME(3) NULL,
    valid_until DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role_scope (tenant_id, user_id, role_id, scope_org_id),
    KEY idx_role_user (tenant_id, role_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_menu (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    menu_code VARCHAR(64) NOT NULL,
    menu_name VARCHAR(100) NOT NULL,
    menu_type VARCHAR(20) NOT NULL,
    route_path VARCHAR(255) NULL,
    component_path VARCHAR(255) NULL,
    permission_code VARCHAR(128) NULL,
    icon VARCHAR(64) NULL,
    visible TINYINT UNSIGNED NOT NULL DEFAULT 1,
    keep_alive TINYINT UNSIGNED NOT NULL DEFAULT 0,
    external_url VARCHAR(500) NULL,
    sort_no INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_menu_code (tenant_id, menu_code),
    KEY idx_parent_sort (tenant_id, parent_id, sort_no),
    KEY idx_permission (tenant_id, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_api_resource (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    resource_name VARCHAR(128) NOT NULL,
    permission_code VARCHAR(128) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    path_pattern VARCHAR(255) NOT NULL,
    controller_method VARCHAR(255) NULL,
    auth_mode VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    risk_level VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_method_path (tenant_id, http_method, path_pattern),
    KEY idx_permission (tenant_id, permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role_menu (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    menu_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, role_id, menu_id),
    KEY idx_menu_role (tenant_id, menu_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role_api (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    api_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, role_id, api_id),
    KEY idx_api_role (tenant_id, api_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role_data_scope (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    resource_code VARCHAR(128) NOT NULL,
    scope_type VARCHAR(20) NOT NULL,
    combine_mode VARCHAR(16) NOT NULL DEFAULT 'UNION',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_resource (tenant_id, role_id, resource_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_role_data_scope_org (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rule_id BIGINT UNSIGNED NOT NULL,
    org_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (tenant_id, rule_id, org_id),
    KEY idx_org_rule (tenant_id, org_id, rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 初始超级管理员与角色
INSERT IGNORE INTO sys_role (id, tenant_id, role_code, role_name, role_type, status, sort_no, created_at, updated_at)
VALUES (1, 0, 'SUPER_ADMIN', '超级管理员', 'SYSTEM', 'ACTIVE', 1, NOW(3), NOW(3));

INSERT IGNORE INTO sys_user (id, tenant_id, username, password_hash, display_name, status, token_version, version, created_at, updated_at)
VALUES (1, 0, 'admin', '{bcrypt}$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96wNz/9H1P4F1q0qU3eZ0q7e2m', '超级管理员', 'ACTIVE', 1, 0, NOW(3), NOW(3));

INSERT IGNORE INTO sys_user_role (id, tenant_id, user_id, role_id, scope_org_id, created_at)
VALUES (1, 0, 1, 1, 0, NOW(3));

-- =====================================================================================
-- 来源迁移：V2__security.sql（原样保留，含原始注释）
-- =====================================================================================

-- V2 安全与审计表
CREATE TABLE IF NOT EXISTS sys_refresh_token (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    session_id VARCHAR(64) NOT NULL,
    family_id VARCHAR(64) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    device_id VARCHAR(128) NULL,
    user_agent VARCHAR(500) NULL,
    ip_address VARCHAR(64) NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    last_used_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    revoke_reason VARCHAR(64) NULL,
    replaced_by_id BIGINT UNSIGNED NULL,
    reuse_detected TINYINT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_token_hash (token_hash),
    KEY idx_user_expiry (tenant_id, user_id, expires_at),
    KEY idx_family (tenant_id, family_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_password_history (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_time (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_login_log (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NULL,
    username VARCHAR(64) NULL,
    login_type VARCHAR(32) NOT NULL,
    result VARCHAR(20) NOT NULL,
    failure_code VARCHAR(64) NULL,
    ip_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    trace_id VARCHAR(64) NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_time (tenant_id, user_id, occurred_at),
    KEY idx_ip_time (ip_address, occurred_at),
    KEY idx_result_time (result, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_operation_log (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    module_code VARCHAR(64) NOT NULL,
    operation_type VARCHAR(64) NOT NULL,
    operation_name VARCHAR(128) NOT NULL,
    operator_id BIGINT UNSIGNED NULL,
    operator_name VARCHAR(100) NULL,
    request_method VARCHAR(16) NULL,
    request_path VARCHAR(500) NULL,
    request_summary JSON NULL,
    result_summary JSON NULL,
    result_code VARCHAR(64) NULL,
    duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
    ip_address VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    success TINYINT UNSIGNED NOT NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_module_time (tenant_id, module_code, occurred_at),
    KEY idx_operator_time (tenant_id, operator_id, occurred_at),
    KEY idx_trace_id (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================================
-- 来源迁移：V3__workflow.sql（原样保留，含原始注释）
-- =====================================================================================

-- V3 工作流扩展表（Flowable 自身 ACT_* 由引擎自动创建）
CREATE TABLE IF NOT EXISTS wf_definition_ext (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    process_key VARCHAR(128) NOT NULL,
    process_name VARCHAR(200) NOT NULL,
    version INT UNSIGNED NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    process_definition_id VARCHAR(128) NOT NULL,
    form_schema JSON NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    published_by BIGINT UNSIGNED NULL,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_version (tenant_id, process_key, version),
    UNIQUE KEY uk_engine_definition (process_definition_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS wf_node_config (
    id BIGINT UNSIGNED NOT NULL,
    definition_ext_id BIGINT UNSIGNED NOT NULL,
    activity_id VARCHAR(128) NOT NULL,
    node_name VARCHAR(200) NOT NULL,
    approval_mode VARCHAR(20) NOT NULL DEFAULT 'ANY',
    approval_ratio DECIMAL(5,2) NULL,
    assignee_type VARCHAR(32) NOT NULL,
    assignee_expression VARCHAR(1000) NOT NULL,
    reject_policy VARCHAR(32) NOT NULL DEFAULT 'PREVIOUS',
    reject_target_activity_id VARCHAR(128) NULL,
    allow_transfer TINYINT UNSIGNED NOT NULL DEFAULT 1,
    allow_delegate TINYINT UNSIGNED NOT NULL DEFAULT 0,
    timeout_minutes INT UNSIGNED NULL,
    config_json JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_definition_activity (definition_ext_id, activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS wf_instance_ext (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    process_instance_id VARCHAR(64) NOT NULL,
    process_definition_id VARCHAR(128) NOT NULL,
    business_type VARCHAR(64) NOT NULL,
    business_id VARCHAR(128) NOT NULL,
    title VARCHAR(300) NOT NULL,
    starter_user_id BIGINT UNSIGNED NOT NULL,
    starter_org_id BIGINT UNSIGNED NULL,
    current_activity_id VARCHAR(128) NULL,
    status VARCHAR(20) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_process_instance (process_instance_id),
    UNIQUE KEY uk_business_instance (tenant_id, business_type, business_id),
    KEY idx_starter_status (tenant_id, starter_user_id, status),
    KEY idx_status_started (tenant_id, status, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS wf_task_ext (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    task_id VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL,
    activity_id VARCHAR(128) NOT NULL,
    assignee_user_id BIGINT UNSIGNED NULL,
    original_assignee_user_id BIGINT UNSIGNED NULL,
    status VARCHAR(20) NOT NULL,
    approval_mode VARCHAR(20) NOT NULL,
    due_at DATETIME(3) NULL,
    claimed_at DATETIME(3) NULL,
    completed_at DATETIME(3) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_id (task_id),
    KEY idx_assignee_status (tenant_id, assignee_user_id, status),
    KEY idx_instance_activity (process_instance_id, activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS wf_approval_record (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    operation_id VARCHAR(64) NOT NULL,
    process_instance_id VARCHAR(64) NOT NULL,
    task_id VARCHAR(64) NULL,
    activity_id VARCHAR(128) NULL,
    action VARCHAR(32) NOT NULL,
    operator_user_id BIGINT UNSIGNED NOT NULL,
    from_user_id BIGINT UNSIGNED NULL,
    to_user_id BIGINT UNSIGNED NULL,
    opinion VARCHAR(2000) NULL,
    attachment_refs JSON NULL,
    snapshot JSON NULL,
    trace_id VARCHAR(64) NULL,
    occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_operation_id (operation_id),
    KEY idx_instance_time (tenant_id, process_instance_id, occurred_at),
    KEY idx_task_time (task_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================================
-- 来源迁移：V4__fix_admin_password.sql（原样保留，含原始注释）
-- =====================================================================================

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
-- 来源迁移：V5__system_modules.sql（原样保留，含原始注释）
-- =====================================================================================

-- V5 系统管理扩展模块：部门(机构)已由 V1 提供，此处新增「字典管理」与「系统变量」表及种子数据。
-- 表名 / 列名与 com.acme.scaffold.jooq.dsl.JooqTables 中的注册严格一致。

CREATE TABLE IF NOT EXISTS sys_dict_type (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    dict_code VARCHAR(64) NOT NULL,
    dict_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_no INT NOT NULL DEFAULT 0,
    remark VARCHAR(255) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_dict_code (tenant_id, dict_code),
    KEY idx_status_sort (tenant_id, status, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_dict_data (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    dict_type_code VARCHAR(64) NOT NULL,
    dict_label VARCHAR(100) NOT NULL,
    dict_value VARCHAR(100) NOT NULL,
    dict_sort INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(255) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_type_value (tenant_id, dict_type_code, dict_value),
    KEY idx_type_sort (tenant_id, dict_type_code, dict_sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    config_key VARCHAR(64) NOT NULL,
    config_name VARCHAR(100) NOT NULL,
    config_value VARCHAR(500) NULL,
    config_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    remark VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config_key (tenant_id, config_key),
    KEY idx_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 种子数据：常用字典与系统参数（幂等，重复执行安全）
INSERT IGNORE INTO sys_dict_type (id, tenant_id, dict_code, dict_name, status, sort_no, remark, created_at, updated_at)
VALUES
    (1, 0, 'sys_normal_disable', '系统开关', 'ACTIVE', 1, '正常 / 停用', NOW(3), NOW(3)),
    (2, 0, 'sys_user_sex', '用户性别', 'ACTIVE', 2, '性别字典', NOW(3), NOW(3)),
    (3, 0, 'sys_yes_no', '是否标识', 'ACTIVE', 3, '是否字典', NOW(3), NOW(3));

INSERT IGNORE INTO sys_dict_data (id, tenant_id, dict_type_code, dict_label, dict_value, dict_sort, status, remark, created_at, updated_at)
VALUES
    (1, 0, 'sys_normal_disable', '正常', '0', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (2, 0, 'sys_normal_disable', '停用', '1', 2, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (3, 0, 'sys_user_sex', '男', '0', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (4, 0, 'sys_user_sex', '女', '1', 2, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (5, 0, 'sys_yes_no', '是', 'Y', 1, 'ACTIVE', NULL, NOW(3), NOW(3)),
    (6, 0, 'sys_yes_no', '否', 'N', 2, 'ACTIVE', NULL, NOW(3), NOW(3));

INSERT IGNORE INTO sys_config (id, tenant_id, config_key, config_name, config_value, config_type, remark, status, created_at, updated_at)
VALUES
    (1, 0, 'sys.title', '系统标题', '管理框架', 'STRING', '前端展示标题', 'ACTIVE', NOW(3), NOW(3)),
    (2, 0, 'sys.max.login.fail', '最大登录失败次数', '5', 'INT', '超过则锁定账户', 'ACTIVE', NOW(3), NOW(3)),
    (3, 0, 'sys.captcha.enabled', '是否启用验证码', 'true', 'BOOLEAN', '登录验证码开关', 'ACTIVE', NOW(3), NOW(3));

-- =====================================================================================
-- 来源迁移：V6__user_single_role_org.sql（原样保留，含原始注释）
-- =====================================================================================

-- V6 用户配置约束收敛：用户仅绑定「单个角色 + 单个部门」。
--
-- 背景：此前模型为多对多（sys_user_role 支持 user->N role、sys_user_org 支持 user->N org），
--       造成权限判定与数据范围计算需要做集合聚合，业务含义模糊、易出现并集越权。
-- 变更：把 role_id / org_id 直接落到 sys_user 实体列，关联表退化为过渡数据并清理。
--
-- 表名 / 列名与 com.acme.scaffold.jooq.dsl.JooqTables 中的注册严格一致。
--
-- 幂等性说明（重要）：
--   本脚本全部 DDL/DML 均以 information_schema 判定做守卫，重复执行结果一致且不报错。
--   原因：MySQL 无事务性 DDL，迁移中途失败时 Flyway 会回滚 schema history 行但保留已生效的结构，
--   重跑同一版本必然撞上 1060 Duplicate column。此前三类场景均需靠幂等自愈：
--     1) 全新空库（列与关联表都不存在）；
--     2) V1 已建关联表的常规库（正常路径）；
--     3) 结构已部分生效、关联表已被删除的「半迁移」库（role_id 已存在但历史行缺失）。

-- 1) sys_user 增加单值外键列（role_id 角色、org_id 部门）：列缺失时才补
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `role_id` BIGINT UNSIGNED NULL COMMENT ''单一角色ID(收敛后不再允许多角色)'' AFTER `primary_org_id`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `org_id` BIGINT UNSIGNED NULL COMMENT ''单一部门ID(收敛后不再允许多部门)'' AFTER `role_id`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 索引同理，缺失才补（重复添加会报 1061 Duplicate key name）
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user` ADD KEY `idx_tenant_role` (`tenant_id`, `role_id`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_role'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user` ADD KEY `idx_tenant_org` (`tenant_id`, `org_id`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_org'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 兼容期保留 primary_org_id：与 org_id 保持一致，供尚未切换的旧代码读取。
UPDATE sys_user SET primary_org_id = org_id WHERE org_id IS NOT NULL AND primary_org_id <=> org_id = 0;

-- 5) 清理多对多关联表（收敛后不再有查询/写入方引用）。
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_user_org;

-- 6) 约束说明：为兼容存量数据，role_id/org_id 暂不建外键与 NOT NULL，
--    「角色必填」由应用层 CreateUserRequest 校验与 UserService 强制保证；
--    存量未绑定角色的用户登录后无任何权限（权限聚合返回空集），符合最小权限原则。

-- =====================================================================================
-- 来源迁移：V7__announcement.sql（原样保留，含原始注释）
-- =====================================================================================

-- V7 新增功能模块：系统公告。
-- 表名 / 列名与 com.acme.scaffold.jooq.dsl.JooqTables 中的 SYS_ANNOUNCEMENT 注册严格一致。
--
-- 状态机：DRAFT(草稿) --publish--> PUBLISHED(已发布) --offline--> OFFLINE(已下线)
-- PUBLISHED 到期(publish_at/expire_at 越界)由定时任务自动下线。
-- 置顶：is_top=1 恒排在其余之前，其次按 publish_at 倒序。
--
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_announcement (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    -- DRAFT / PUBLISHED / OFFLINE
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    -- 是否置顶
    is_top TINYINT UNSIGNED NOT NULL DEFAULT 0,
    -- 定时生效时间(为空表示发布即生效)；<= NOW 时对 PUBLISHED 可见
    publish_at DATETIME(3) NULL,
    -- 有效期截止(为空表示长期有效)；< NOW 时自动下线
    expire_at DATETIME(3) NULL,
    -- 实际发布时间 / 下线时间
    published_at DATETIME(3) NULL,
    offline_at DATETIME(3) NULL,
    publisher_id BIGINT UNSIGNED NULL,
    view_count INT UNSIGNED NOT NULL DEFAULT 0,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_status_pub (tenant_id, status, publish_at),
    KEY idx_tenant_top (tenant_id, is_top, publish_at),
    KEY idx_expire (tenant_id, status, expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================================
-- 来源迁移：V8__message.sql（原样保留，含原始注释）
-- =====================================================================================

-- V8 新增功能模块：站内信（发件主表 + 收件明细）。
-- 表名 / 列名与 com.acme.scaffold.jooq.dsl.JooqTables 中的 SYS_MESSAGE / SYS_MESSAGE_RECEIPT 注册严格一致。
--
-- sys_message 为发件主表(一条消息一次发送动作)，sys_message_receipt 为收件明细。
-- 「批量发送」= 按角色/部门筛选接收人后在 receipt 表批量插入(同一 message_id 多行)。
-- 已读状态与回执时间记录在 receipt 行：is_read / read_at。
CREATE TABLE IF NOT EXISTS sys_message (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    title VARCHAR(200) NOT NULL,
    content MEDIUMTEXT NOT NULL,
    -- NOTICE(通知) / ALERT(告警) / SYSTEM(系统)
    msg_type VARCHAR(20) NOT NULL DEFAULT 'NOTICE',
    sender_id BIGINT UNSIGNED NULL,
    -- 接收人筛选快照：便于审计「当时按什么范围发的」
    filter_role_ids VARCHAR(500) NULL,
    filter_org_ids VARCHAR(500) NULL,
    -- 单条发送时的显式接收人
    receiver_ids VARCHAR(500) NULL,
    total_count INT UNSIGNED NOT NULL DEFAULT 0,
    read_count INT UNSIGNED NOT NULL DEFAULT 0,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_sent (tenant_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS sys_message_receipt (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    message_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    -- 0=未读 1=已读
    is_read TINYINT UNSIGNED NOT NULL DEFAULT 0,
    -- 已读回执时间
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_msg_user (tenant_id, message_id, user_id),
    KEY idx_user_read (tenant_id, user_id, is_read),
    KEY idx_msg (tenant_id, message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================================
-- 来源迁移：V9__monitor_sample.sql（原样保留，含原始注释）
-- =====================================================================================

-- V9 新增功能模块：系统监控采样。
-- 表名 / 列名与 com.acme.scaffold.jooq.dsl.JooqTables 中的 SYS_MONITOR_SAMPLE 注册严格一致。
--
-- 定时任务(JobRunr)周期采样 CPU/内存/磁盘/JVM 与在线用户数，写入本表，
-- 供「按时间范围查询」与趋势图使用，避免实时计算带来的采集开销。
CREATE TABLE IF NOT EXISTS sys_monitor_sample (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    -- CPU 使用率(%)
    cpu_usage DECIMAL(5,2) NULL,
    -- JVM 堆内存使用率(%)
    memory_usage DECIMAL(5,2) NULL,
    -- 物理内存使用率(%)
    system_memory_usage DECIMAL(5,2) NULL,
    -- 磁盘使用率(%)
    disk_usage DECIMAL(5,2) NULL,
    used_heap_bytes BIGINT UNSIGNED NULL,
    max_heap_bytes BIGINT UNSIGNED NULL,
    used_memory_bytes BIGINT UNSIGNED NULL,
    total_memory_bytes BIGINT UNSIGNED NULL,
    -- 在线用户数(活跃刷新令牌/未过期会话)
    online_users INT UNSIGNED NOT NULL DEFAULT 0,
    active_sessions INT UNSIGNED NOT NULL DEFAULT 0,
    thread_count INT UNSIGNED NOT NULL DEFAULT 0,
    -- 采集时刻
    sampled_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================================
-- 来源迁移：V10__user_profile.sql（原样保留，含原始注释）
-- =====================================================================================

-- V10 个人中心与账号设置：为 sys_user 补充头像与偏好字段。
--
-- 说明：
--   手机号 / 邮箱绑定复用 V1 已有的 mobile、email 列，此处仅新增「偏好」类字段，
--   避免为同一语义重复建列。绑定状态由列是否为空推导，无需额外标记位。
--
-- 幂等性：所有 DDL 均以 information_schema 判定做守卫，重复执行不报错
-- （与 V6 保持同一风格，MySQL 无事务 DDL，迁移中途失败后可安全重跑）。

-- 1) 头像：存相对路径（如 /uploads/avatar/1_1710000000.png），不存二进制
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `avatar_url` VARCHAR(255) NULL COMMENT ''头像地址(相对路径)'' AFTER `email`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'avatar_url'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 通知偏好：三个开关独立列，便于「只开站内信、关邮件」这类组合
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_site_message` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''站内信通知开关'' AFTER `avatar_url`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_site_message'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_email` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''邮件通知开关'' AFTER `notify_site_message`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_email'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''短信通知开关'' AFTER `notify_email`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_mobile'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3) 隐私偏好：登录日志展示、脱敏手机号、允许被搜索
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `show_login_log` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''是否展示我的登录记录'' AFTER `notify_mobile`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'show_login_log'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `mask_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''对外展示时脱敏手机号'' AFTER `show_login_log`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'mask_mobile'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `discoverable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''允许被其他用户搜索到'' AFTER `mask_mobile`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'discoverable'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 登录设备索引：支撑「我的登录设备」按用户聚合查询
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_refresh_token`
             ADD KEY `idx_user_revoked` (`user_id`, `revoked_at`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_refresh_token' AND INDEX_NAME = 'idx_user_revoked'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================================
-- 来源迁移：V11__api_resource_seed.sql（原样保留，含原始注释）
-- =====================================================================================

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
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000002, 0, '分页查询公告（支持标题/内容关键词搜索）', 'system:announcement:read', 'GET', '/api/system/announcements/page', 'AnnouncementController#page', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000003, 0, '删除公告（逻辑删除）', 'system:announcement:delete', 'DELETE', '/api/system/announcements/{id}', 'AnnouncementController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000004, 0, '公告详情（累加浏览次数）', 'system:announcement:read', 'GET', '/api/system/announcements/{id}', 'AnnouncementController#get', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000005, 0, '编辑公告（仅草稿/已下线可编辑）', 'system:announcement:update', 'PUT', '/api/system/announcements/{id}', 'AnnouncementController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000006, 0, '下线公告', 'system:announcement:publish', 'POST', '/api/system/announcements/{id}/offline', 'AnnouncementController#offline', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000007, 0, '发布公告（支持定时生效：publishAt 为空则立即生效）', 'system:announcement:publish', 'POST', '/api/system/announcements/{id}/publish', 'AnnouncementController#publish', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000008, 0, '切换置顶', 'system:announcement:update', 'POST', '/api/system/announcements/{id}/toggle-top', 'AnnouncementController#toggleTop', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000009, 0, '接口资源列表', 'system:api:read', 'GET', '/api/system/api-resources', 'ApiResourceController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000010, 0, '注册接口资源', 'system:api:create', 'POST', '/api/system/api-resources', 'ApiResourceController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000011, 0, '扫描接口资源并预览差异（不写库）', 'system:api:read', 'GET', '/api/system/api-resources/scan', 'ApiResourceController#scanPreview', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000012, 0, '执行接口资源扫描同步（写库）', 'system:api:sync', 'POST', '/api/system/api-resources/scan/sync', 'ApiResourceController#sync', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000013, 0, '删除接口资源', 'system:api:delete', 'DELETE', '/api/system/api-resources/{id}', 'ApiResourceController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000014, 0, '更新接口资源', 'system:api:update', 'PUT', '/api/system/api-resources/{id}', 'ApiResourceController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000015, 0, '配置列表', 'system:config:read', 'GET', '/api/system/configs', 'ConfigController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000016, 0, '创建配置', 'system:config:create', 'POST', '/api/system/configs', 'ConfigController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000017, 0, '按配置键动态读取', 'system:config:read', 'GET', '/api/system/configs/key/{key}', 'ConfigController#getByKey', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000018, 0, '删除配置', 'system:config:delete', 'DELETE', '/api/system/configs/{id}', 'ConfigController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000019, 0, '更新配置', 'system:config:update', 'PUT', '/api/system/configs/{id}', 'ConfigController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000020, 0, '字典数据列表（按字典类型筛选）', 'system:dict:read', 'GET', '/api/system/dict-data', 'DictDataController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000021, 0, '创建字典数据', 'system:dict:create', 'POST', '/api/system/dict-data', 'DictDataController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000022, 0, '删除字典数据', 'system:dict:delete', 'DELETE', '/api/system/dict-data/{id}', 'DictDataController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000023, 0, '更新字典数据', 'system:dict:update', 'PUT', '/api/system/dict-data/{id}', 'DictDataController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000024, 0, '字典类型列表', 'system:dict:read', 'GET', '/api/system/dict-types', 'DictTypeController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000025, 0, '创建字典类型', 'system:dict:create', 'POST', '/api/system/dict-types', 'DictTypeController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000026, 0, '删除字典类型（存在字典数据时拒绝）', 'system:dict:delete', 'DELETE', '/api/system/dict-types/{id}', 'DictTypeController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000027, 0, '更新字典类型', 'system:dict:update', 'PUT', '/api/system/dict-types/{id}', 'DictTypeController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000028, 0, '创建菜单', 'system:menu:create', 'POST', '/api/system/menus', 'MenuController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000029, 0, '菜单树', 'system:menu:read', 'GET', '/api/system/menus/tree', 'MenuController#tree', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000030, 0, '删除菜单', 'system:menu:delete', 'DELETE', '/api/system/menus/{id}', 'MenuController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000031, 0, '更新菜单', 'system:menu:update', 'PUT', '/api/system/menus/{id}', 'MenuController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000032, 0, '发送站内信（单条指定接收人，或按角色/部门批量发送）', 'system:message:send', 'POST', '/api/system/messages/send', 'MessageController#send', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000033, 0, '我发出的消息分页（含已读统计）', 'system:message:read', 'GET', '/api/system/messages/sent', 'MessageController#sent', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000034, 0, '异常日志分页（按时间范围查询关键接口/任务失败记录）', 'system:monitor:read', 'GET', '/api/system/monitor/error-logs', 'MonitorController#errorLogs', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000035, 0, '异常日志按模块/错误码聚合', 'system:monitor:read', 'GET', '/api/system/monitor/error-summary', 'MonitorController#errorSummary', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000036, 0, '实时运行指标（CPU/内存/磁盘/JVM）', 'system:monitor:read', 'GET', '/api/system/monitor/metrics', 'MonitorController#metrics', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000037, 0, '在线会话明细（可按用户名/姓名筛选）', 'system:monitor:read', 'GET', '/api/system/monitor/online-sessions', 'MonitorController#onlineSessions', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000038, 0, '在线用户与会话汇总', 'system:monitor:read', 'GET', '/api/system/monitor/online-summary', 'MonitorController#onlineSummary', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000039, 0, '手动触发一次采样（供前端刷新按钮使用）', 'system:monitor:read', 'POST', '/api/system/monitor/sample', 'MonitorController#sample', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000040, 0, '历史采样趋势（按时间范围）', 'system:monitor:read', 'GET', '/api/system/monitor/samples', 'MonitorController#samples', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000041, 0, '创建机构', 'system:org:create', 'POST', '/api/system/orgs', 'OrgController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000042, 0, '机构树', 'system:org:read', 'GET', '/api/system/orgs/tree', 'OrgController#tree', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000043, 0, '删除机构（存在子机构时拒绝）', 'system:org:delete', 'DELETE', '/api/system/orgs/{id}', 'OrgController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000044, 0, '更新机构（支持调整上级与层级移动）', 'system:org:update', 'PUT', '/api/system/orgs/{id}', 'OrgController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000045, 0, '角色列表', 'system:role:read', 'GET', '/api/system/roles', 'RoleController#list', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000046, 0, '创建角色', 'system:role:create', 'POST', '/api/system/roles', 'RoleController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000047, 0, '删除角色', 'system:role:delete', 'DELETE', '/api/system/roles/{id}', 'RoleController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000048, 0, '角色详情（含菜单/接口权限）', 'system:role:read', 'GET', '/api/system/roles/{id}', 'RoleController#get', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000049, 0, '更新角色', 'system:role:update', 'PUT', '/api/system/roles/{id}', 'RoleController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000050, 0, '创建用户', 'system:user:create', 'POST', '/api/system/users', 'UserController#create', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000051, 0, '分页查询用户', 'system:user:read', 'GET', '/api/system/users/page', 'UserController#page', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000052, 0, '删除用户（物理删除/硬删除）', 'system:user:delete', 'DELETE', '/api/system/users/{id}', 'UserController#delete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000053, 0, '用户详情', 'system:user:read', 'GET', '/api/system/users/{id}', 'UserController#get', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000054, 0, '更新用户', 'system:user:update', 'PUT', '/api/system/users/{id}', 'UserController#update', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000055, 0, '重置密码', 'system:user:update', 'POST', '/api/system/users/{id}/reset-password', 'UserController#resetPassword', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000056, 0, '我发起的流程', 'workflow:instance:read', 'GET', '/api/workflow/instances/mine', 'InstanceController#mine', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000057, 0, '发起审批流程', 'workflow:instance:start', 'POST', '/api/workflow/instances/start', 'InstanceController#start', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000058, 0, '流程审批记录', 'workflow:instance:read', 'GET', '/api/workflow/instances/{processInstanceId}/records', 'InstanceController#records', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000059, 0, '审批/驳回任务', 'workflow:task:approve', 'POST', '/api/workflow/tasks/complete', 'TaskController#complete', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000060, 0, '我的待办任务', 'workflow:task:read', 'GET', '/api/workflow/tasks/mine', 'TaskController#mine', 'REQUIRED', 'ACTIVE', 'NORMAL');
INSERT INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level) VALUES (900000000061, 0, '转办任务', 'workflow:task:transfer', 'POST', '/api/workflow/tasks/transfer', 'TaskController#transfer', 'REQUIRED', 'ACTIVE', 'NORMAL');

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
-- 来源迁移：V12__role_data_scope_api.sql（原样保留，含原始注释）
-- =====================================================================================

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

INSERT IGNORE INTO sys_api_resource
    (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method,
     auth_mode, status, risk_level)
VALUES (900000000063, 0, '保存角色数据权限（覆盖式）', 'system:role:update', 'PUT',
        '/api/system/roles/{id}/data-scopes', 'RoleController#saveDataScope', 'REQUIRED', 'ACTIVE', 'NORMAL');

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
-- 来源迁移：V13__workflow_design.sql（原样保留，含原始注释）
-- =====================================================================================

-- V13 自定义工作流设计表：支持用户动态新增/删除/排序审批节点与执行步骤、
-- 配置条件分支，保存为草稿并发布为可生效的 Flowable 流程定义。
CREATE TABLE IF NOT EXISTS wf_workflow_design (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    process_key VARCHAR(128) NOT NULL,
    process_name VARCHAR(200) NOT NULL,
    description VARCHAR(500) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    nodes_json JSON NULL,
    edges_json JSON NULL,
    form_schema JSON NULL,
    bpmn_xml LONGTEXT NULL,
    deployment_id VARCHAR(64) NULL,
    process_definition_id VARCHAR(128) NULL,
    published_by BIGINT UNSIGNED NULL,
    published_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_key (tenant_id, process_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================================
-- 来源迁移：V14__workflow_definition_resource.sql（原样保留，含原始注释）
-- =====================================================================================

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
-- 来源迁移：V15__user_role_org_many_to_many.sql（原样保留，含原始注释）
-- =====================================================================================

-- V15 用户-角色 / 用户-部门 恢复多对多（回退 V6 单值收敛）
--
-- 背景：V6 把 sys_user_role / sys_user_org 收敛为 sys_user.role_id / org_id 单列，
--       现按业务需要恢复 N:N：一个用户可拥有多个角色、归属多个部门（含主角色/主部门标记）。
-- 幂等：所有 DDL/DML 均以 information_schema 守卫，重复执行结果一致。
-- 表名 / 列名与 com.acme.scaffold.jooq.dsl.JooqTables 注册一致。

-- 1) 新建用户-角色关联表
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE TABLE `sys_user_role` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `user_id` BIGINT UNSIGNED NOT NULL,
            `role_id` BIGINT UNSIGNED NOT NULL,
            `is_primary` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''是否主角色'',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_user_role` (`tenant_id`, `user_id`, `role_id`),
            KEY `idx_role` (`tenant_id`, `role_id`),
            KEY `idx_user` (`tenant_id`, `user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci',
        'DO 0')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_role'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2) 新建用户-部门关联表
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'CREATE TABLE `sys_user_org` (
            `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
            `tenant_id` BIGINT UNSIGNED NOT NULL DEFAULT 0,
            `user_id` BIGINT UNSIGNED NOT NULL,
            `org_id` BIGINT UNSIGNED NOT NULL,
            `is_primary` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''是否主部门'',
            `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_user_org` (`tenant_id`, `user_id`, `org_id`),
            KEY `idx_org` (`tenant_id`, `org_id`),
            KEY `idx_user` (`tenant_id`, `user_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci',
        'DO 0')
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user_org'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

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
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'INSERT IGNORE INTO sys_user_org (tenant_id, user_id, org_id, is_primary, created_at)
         SELECT 0, id, org_id, 1, NOW(3) FROM sys_user WHERE org_id IS NOT NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 收敛列下线：删除 sys_user 的 role_id / org_id / primary_org_id（含索引）。
--    删除前关联数据已写入 sys_user_role / sys_user_org，主角色/主部门改由关联表 is_primary 推导。
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP INDEX `idx_tenant_role`', 'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_role'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP INDEX `idx_tenant_org`', 'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_org'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `role_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `org_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `primary_org_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'primary_org_id'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- =====================================================================================
-- 来源迁移：V16__idempotency_record.sql（原样保留，含原始注释）
-- =====================================================================================

-- 幂等性记录表（设计方案 §12：写接口支持 Idempotency-Key，审批类接口强制使用）
--
-- 设计要点：
-- 1) 唯一索引 uk_idem_key_scope(tenant_id, idempotency_key, api_scope) 是并发去重的最終兜底：
--    即使应用层判断与插入之间有时间窗，数据库也会拒绝第二条登记，Aspect 捕获后返回 409。
-- 2) 记录与业务数据分属不同事务（Aspect 以 REQUIRES_NEW 写入），
--    因此业务回滚不会把幂等记录一并抹掉，避免「失败后又允许重复提交」。
-- 3) response_snapshot 用于重复请求时重放首次响应，做到「多次调用 = 一次效果 + 一致返回值」。
-- 4) expires_at 由 JobRunr 周期任务清理，避免表无限膨胀。

CREATE TABLE IF NOT EXISTS sys_idempotency_record (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(128) NOT NULL,
    api_scope VARCHAR(128) NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    request_fingerprint VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    response_snapshot JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem_key_scope (tenant_id, idempotency_key, api_scope),
    KEY idx_idem_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- =====================================================================================
-- 来源迁移：V17__menu_role_seed.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- =====================================================================================
-- 来源迁移：V18__workflow_role_seed.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- 绑定到超级管理员（user_id=1）；生产环境按需分配给真实财务/主管人员
INSERT IGNORE INTO sys_user_role (tenant_id, user_id, role_id, is_primary, created_at)
VALUES (0, 1, 2, 0, NOW(3)),
       (0, 1, 3, 0, NOW(3));

-- =====================================================================================
-- 来源迁移：V19__usage_report_menu.sql（原样保留，含原始注释）
-- =====================================================================================

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

INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
  (900000000101, 0, '使用报表（访问量 / 功能使用频次 / 活跃用户数）', 'report:usage:read', 'GET', '/api/workbench/report', 'UsageReportController#report', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- SUPER_ADMIN 绑定「使用报表」菜单与接口资源（tenant_id, role_id, menu_id / api_id 为主键或唯一键）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id = 1030;

INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, id, CURRENT_TIMESTAMP(3) FROM sys_api_resource WHERE id = 900000000101;

-- =====================================================================================
-- 来源迁移：V20__report_management_rename_fix.sql（原样保留，含原始注释）
-- =====================================================================================

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

UPDATE sys_api_resource
SET resource_name   = '报表管理（使用统计 / 类别统计 / 状态统计）',
    permission_code = 'report:read',
    path_pattern    = '/api/report/**',
    controller_method = 'ReportController',
    updated_at     = NOW(3)
WHERE id = 900000000101;

-- =====================================================================================
-- 来源迁移：V21__job_management.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- =====================================================================================
-- 来源迁移：V22__codegen_menu.sql（原样保留，含原始注释）
-- =====================================================================================

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

INSERT IGNORE INTO sys_api_resource (id, tenant_id, resource_name, permission_code, http_method, path_pattern, controller_method, auth_mode, status, risk_level)
VALUES
  (900000000201, 0, '代码生成（表清单 / 字段元信息 / 生成预览）', 'codegen:read', 'GET', '/api/codegen/tables', 'CodeGenController#tables', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000202, 0, '代码生成（表字段元信息）', 'codegen:read', 'GET', '/api/codegen/tables/{tableName}', 'CodeGenController#tableMeta', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000203, 0, '代码生成（生成预览）', 'codegen:read', 'POST', '/api/codegen/preview', 'CodeGenController#preview', 'REQUIRED', 'ACTIVE', 'NORMAL'),
  (900000000204, 0, '代码生成（zip 打包下载）', 'codegen:download', 'POST', '/api/codegen/download', 'CodeGenController#download', 'REQUIRED', 'ACTIVE', 'NORMAL');

-- SUPER_ADMIN 绑定「代码生成」菜单与接口资源
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id = 1040;

INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, id, CURRENT_TIMESTAMP(3) FROM sys_api_resource WHERE id IN (900000000201, 900000000202, 900000000203, 900000000204);

-- =====================================================================================
-- 来源迁移：V23__workflow_design_deleted.sql（原样保留，含原始注释）
-- =====================================================================================

-- V23 工作流设计表补逻辑删除列：修复「删除即物理消失、前端陈旧行操作报不存在」的数据一致性缺陷。
-- 背景：wf_workflow_design 原无 deleted 列，JooqWriters.delete 的逻辑删除退化为物理删除，
-- 删除后行不可追溯，且同一 processKey 可被无痕重建（seeder 反复播种产生新 id）。
-- 本迁移补列后，服务层删除走 deleted=1 逻辑删除；既有行默认 0（可见），不影响存量数据。
ALTER TABLE wf_workflow_design
    ADD COLUMN deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER version,
    ADD KEY idx_tenant_status_deleted (tenant_id, status, deleted);

-- =====================================================================================
-- 来源迁移：V24__report_menu_split.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- 2) 6 个报表子菜单（每个原页签一个独立入口）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES
  (1050, 0, 1030, 'report-usage',     '使用统计',         'MENU', '/report/usage',     NULL, 'report:read', 'DataLine',    1, 0, 10, 'ACTIVE', NOW(3), NOW(3)),
  (1051, 0, 1030, 'report-category',  '类别统计',         'MENU', '/report/category',  NULL, 'report:read', 'PieChart',    1, 0, 20, 'ACTIVE', NOW(3), NOW(3)),
  (1052, 0, 1030, 'report-status',    '状态统计',         'MENU', '/report/status',    NULL, 'report:read', 'DataAnalysis',1, 0, 30, 'ACTIVE', NOW(3), NOW(3)),
  (1053, 0, 1030, 'report-job-exec',  '定时任务执行统计', 'MENU', '/report/job-exec',  NULL, 'report:read', 'Timer',       1, 0, 40, 'ACTIVE', NOW(3), NOW(3)),
  (1054, 0, 1030, 'report-login',     '登录统计',         'MENU', '/report/login',     NULL, 'report:read', 'User',        1, 0, 50, 'ACTIVE', NOW(3), NOW(3)),
  (1055, 0, 1030, 'report-api-call',  '接口调用统计',     'MENU', '/report/api-call',  NULL, 'report:read', 'Connection',  1, 0, 60, 'ACTIVE', NOW(3), NOW(3));

-- 3) SUPER_ADMIN 绑定 6 个报表子菜单（父菜单 1030 的既有绑定保留，目录可见性不受影响）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE id BETWEEN 1050 AND 1055;

-- =====================================================================================
-- 来源迁移：V25__menu_sort_api.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- SUPER_ADMIN 绑定（与 V17/V19/V21/V22 一致，role_id = 1）
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, r.id, CURRENT_TIMESTAMP(3) FROM sys_api_resource r
WHERE r.id = 900000000409
  AND NOT EXISTS (SELECT 1 FROM sys_role_api ra WHERE ra.role_id = 1 AND ra.api_id = r.id);

-- =====================================================================================
-- 来源迁移：V26__org_seed.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- 2) admin（id=1）主部门关联：使 INITIATOR_MANAGER 解析可拿到部门，
--    且 admin 已绑定 MANAGER/FINANCE 角色（sys_user_role 种子），部门内主管解析非空
INSERT IGNORE INTO sys_user_org (tenant_id, user_id, org_id, is_primary)
VALUES (0, 1, 1, 1);

-- 3) 测试用户 zhangsan（id=2）：满足「至少 2 个用户」的消息/审批场景；
--    password_hash 复制 admin（同库 SELECT，登录密码与 admin 相同）
INSERT IGNORE INTO sys_user (id, username, password_hash, display_name)
SELECT 2, 'zhangsan', password_hash, '张三' FROM sys_user WHERE id = 1;

-- =====================================================================================
-- 来源迁移：V27__online_user_management.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- =====================================================================================
-- 来源迁移：V28__grant_missing_api_to_super_admin.sql（原样保留，含原始注释）
-- =====================================================================================

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
-- 来源迁移：V29__monitor_log_menus.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- SUPER_ADMIN 绑定新菜单（与 V17 种子一致，role_id = 1）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, m.id
FROM sys_menu m
WHERE m.id IN (1061, 1062, 1063, 1064)
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

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

-- SUPER_ADMIN 绑定（与 V17/V19/V21/V22/V24/V25/V27 一致，role_id = 1）
INSERT IGNORE INTO sys_role_api (tenant_id, role_id, api_id, created_at)
SELECT 0, 1, r.id, CURRENT_TIMESTAMP(3) FROM sys_api_resource r
WHERE r.id BETWEEN 900000000415 AND 900000000419
  AND NOT EXISTS (SELECT 1 FROM sys_role_api ra WHERE ra.role_id = 1 AND ra.api_id = r.id);

-- =====================================================================================
-- 来源迁移：V30__ops_menu_restructure.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- SUPER_ADMIN 绑定新目录（与 V17/V29 种子一致，role_id = 1）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, m.id FROM sys_menu m WHERE m.id = 1070;

-- -------------------------------------------------------------
-- 2) 软删 3 个公共统计页 + 「报表管理」目录容器，并清理角色-菜单关联
--    （MenuService.delete 同类问题：N:N 关联表无外键，须显式清理防孤儿行）
-- -------------------------------------------------------------
UPDATE sys_menu SET deleted = 1, updated_at = NOW(3)
WHERE id IN (1050, 1051, 1052) AND deleted = 0;
DELETE FROM sys_role_menu WHERE menu_id IN (1050, 1051, 1052);

UPDATE sys_menu SET deleted = 1, updated_at = NOW(3)
WHERE id = 1030 AND deleted = 0;
DELETE FROM sys_role_menu WHERE menu_id = 1030;

-- -------------------------------------------------------------
-- 3) 7 个功能菜单迁入运维管理，sort_no 按需求顺序重排
--    接口资源管理 10 / 代码生成 20 / 系统监控 30 / 缓存监控 40 /
--    连接池监控 50 / 登录日志 60 / 操作日志 70
-- -------------------------------------------------------------
UPDATE sys_menu SET parent_id = 1070, sort_no = 10, updated_at = NOW(3)
WHERE id = 1014 AND parent_id <> 1070;
UPDATE sys_menu SET parent_id = 1070, sort_no = 20, updated_at = NOW(3)
WHERE id = 1040 AND parent_id <> 1070;
UPDATE sys_menu SET parent_id = 1070, sort_no = 30, updated_at = NOW(3)
WHERE id = 1018 AND parent_id <> 1070;
UPDATE sys_menu SET parent_id = 1070, sort_no = 40, updated_at = NOW(3)
WHERE id = 1061 AND parent_id <> 1070;
UPDATE sys_menu SET parent_id = 1070, sort_no = 50, updated_at = NOW(3)
WHERE id = 1062 AND parent_id <> 1070;
UPDATE sys_menu SET parent_id = 1070, sort_no = 60, updated_at = NOW(3)
WHERE id = 1063 AND parent_id <> 1070;
UPDATE sys_menu SET parent_id = 1070, sort_no = 70, updated_at = NOW(3)
WHERE id = 1064 AND parent_id <> 1070;

-- -------------------------------------------------------------
-- 4) 保留的 3 个运维向统计页挂到运维管理（原父目录 1030 已软删）
--    定时任务执行统计 80 / 登录统计 90 / 接口调用统计 100
-- -------------------------------------------------------------
UPDATE sys_menu SET parent_id = 1070, sort_no = 80, updated_at = NOW(3)
WHERE id = 1053 AND parent_id <> 1070;
UPDATE sys_menu SET parent_id = 1070, sort_no = 90, updated_at = NOW(3)
WHERE id = 1054 AND parent_id <> 1070;
UPDATE sys_menu SET parent_id = 1070, sort_no = 100, updated_at = NOW(3)
WHERE id = 1055 AND parent_id <> 1070;

-- =====================================================================================
-- 来源迁移：V31__report_menu_restore.sql（原样保留，含原始注释）
-- =====================================================================================

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

-- SUPER_ADMIN 重新绑定目录（V30 清理过该绑定，与 V17/V29 种子一致，role_id = 1）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, m.id
FROM sys_menu m
WHERE m.id = 1030
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = 1 AND rm.menu_id = m.id);

-- -------------------------------------------------------------
-- 2) 3 个统计页从「运维管理」迁回「报表管理」，sort_no 按 10/20/30 重排
--    定时任务执行统计(1053) / 登录统计(1054) / 接口调用统计(1055)
--    （原 1050/1051/1052 公共统计页保持软删，不复活）
-- -------------------------------------------------------------
UPDATE sys_menu SET parent_id = 1030, sort_no = 10, updated_at = NOW(3)
WHERE id = 1053 AND parent_id <> 1030;
UPDATE sys_menu SET parent_id = 1030, sort_no = 20, updated_at = NOW(3)
WHERE id = 1054 AND parent_id <> 1030;
UPDATE sys_menu SET parent_id = 1030, sort_no = 30, updated_at = NOW(3)
WHERE id = 1055 AND parent_id <> 1030;

