-- ============================================================
-- 全量初始化脚本（新库专用 / 手工执行）
-- 由 scripts/build_reference_modular.py 从 db/migration/ 版本化结构链
-- （V32~V84，共 52 个 SQL 文件）按版本号顺序逐字拼接生成，语句顺序与历史执行顺序一致。
--
-- 【执行方式】mysql --default-character-set=utf8mb4 -u<user> -p <db> < 01_full_schema.sql
-- 【前置条件】先执行 00_create_database.sql 建库（utf8mb4_0900_ai_ci）。
-- 【幂等性】仅适用于空库一次执行；内含非幂等的 CREATE TABLE / ALTER / CREATE INDEX，
--           重复执行会报错。存量库升级请按版本自行评估差异，不要重跑本脚本。
-- 【历史注释】下方 V*.sql 原文中出现的 Flyway 字样为历史说明，当前应用已不加载
--           Flyway、不自动执行任何 DDL；保留原文以便逐版本追溯。
-- 【基线标记】末尾写入 schema_baseline，供 scripts/init/verify_install.py 校验。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================


-- ############################################################
-- 来源: V32__consolidated_schema.sql
-- ############################################################

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
  (1013, 0, 1002, 'system:department',  '部门管理',       'MENU', '/system/department',  NULL, 'system:org:read',        'OfficeBuilding',1, 0, 40, 'ACTIVE', NOW(3), NOW(3)),
  (1014, 0, 1002, 'system:api-resource','接口资源管理',   'MENU', '/system/api-resource', NULL, 'system:api:read',        'Connection',    1, 0, 50, 'ACTIVE', NOW(3), NOW(3)),
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

-- ############################################################
-- 来源: V33__file_management.sql
-- ############################################################

-- V33 文件管理（运维管理 → 文件管理）
-- 依赖：V32（含「运维管理」一级目录 id=1070，menu_code='ops'）。
-- 接口资源（system:file:upload/read/download/delete）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 文件表：记录原始名、服务端存储名、大小、类型、来源功能模块、上传人、下载次数。
--    deleted/version 支持逻辑删除（删除时仅清理物理文件，保留记录供审计）。
CREATE TABLE IF NOT EXISTS sys_file (
    id            BIGINT UNSIGNED NOT NULL,
    tenant_id     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    original_name VARCHAR(255)    NOT NULL,
    stored_name   VARCHAR(255)    NOT NULL COMMENT '服务端生成的存储文件名（UUID.ext），不含目录',
    storage_path  VARCHAR(500)    NOT NULL COMMENT '上传时的绝对存储路径（记录用，读取以 baseDir+stored_name 为准）',
    file_size     BIGINT          NOT NULL,
    content_type  VARCHAR(128)    NULL,
    source_module VARCHAR(64)     NOT NULL COMMENT '来源功能模块，如 workflow/report/file-management',
    biz_id        VARCHAR(64)     NULL COMMENT '业务关联 ID（可选，如流程实例/报表 ID）',
    uploader_id   BIGINT UNSIGNED NULL,
    uploader_name VARCHAR(100)    NULL COMMENT '上传人姓名（冗余存储，用户删除后仍可读）',
    download_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    version       INT UNSIGNED    NOT NULL DEFAULT 0,
    deleted       TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_source (tenant_id, source_module),
    KEY idx_tenant_uploader (tenant_id, uploader_id),
    KEY idx_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：文件管理（挂在「运维管理」1070 下，sort_no=110 置于末尾）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1080, 0, 1070, 'file-management', '文件管理', 'MENU', '/system/file', 'system/FileManagement', 'system:file:read', 'Document', 1, 0, 110, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1080;

-- ############################################################
-- 来源: V34__csv_export_center.sql
-- ############################################################

-- V34 CSV 导出中心（系统管理 → 导出中心）
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:export:read）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 菜单：导出中心（挂在「系统管理」1002 下，sort_no=100 置于末尾）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1090, 0, 1002, 'system:export', '导出中心', 'MENU', '/system/export', 'system/ExportCenter', 'system:export:read', 'Download', 1, 0, 100, 'ACTIVE', NOW(3), NOW(3));

-- 2) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1090;

-- ############################################################
-- 来源: V35__post_management.sql
-- ############################################################

-- V35 岗位管理（系统管理 → 岗位管理）
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:post:read 等）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 岗位表：编码租户内唯一；软删除 + 乐观锁，范式对齐 sys_role/sys_dict_type
CREATE TABLE IF NOT EXISTS sys_post (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    post_code VARCHAR(64) NOT NULL,
    post_name VARCHAR(100) NOT NULL,
    post_sort INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    remark VARCHAR(512) NULL,
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_post_code (tenant_id, post_code),
    KEY idx_status_sort (tenant_id, status, post_sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 用户-岗位关联（多对多）。无 deleted 列：分配为整体替换（物理删+插），范式对齐 sys_user_org
CREATE TABLE IF NOT EXISTS sys_user_post (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    post_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_post (tenant_id, user_id, post_id),
    KEY idx_post_user (tenant_id, post_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 3) 菜单：岗位管理（挂在「系统管理」1002 下，sort_no=110 承接导出中心 100 之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1091, 0, 1002, 'system:post', '岗位管理', 'MENU', '/system/post', 'system/PostView', 'system:post:read', 'Position', 1, 0, 110, 'ACTIVE', NOW(3), NOW(3));

-- 4) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1091;

-- ############################################################
-- 来源: V36__import_center.sql
-- ############################################################

-- V36 导入中心（系统管理 → 导入中心），与 V34 导出中心对称。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:import:read / system:import:write）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 菜单：导入中心（挂在「系统管理」1002 下，sort_no=101 紧随导出中心）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1091, 0, 1002, 'system:import', '导入中心', 'MENU', '/system/import', 'system/ImportCenter', 'system:import:read', 'Upload', 1, 0, 101, 'ACTIVE', NOW(3), NOW(3));

-- 2) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1091;

-- ############################################################
-- 来源: V37__sms_record.sql
-- ############################################################

-- V37 短信发送记录（系统管理 → 短信记录），补全短信通知渠道（M6）的运维对账面。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:sms-record:read）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 表：sys_sms_record（脱敏存储：手机号掩码、正文摘要，PII 不明文落库）
CREATE TABLE IF NOT EXISTS sys_sms_record (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    batch_no VARCHAR(64) NOT NULL,
    mobiles_masked VARCHAR(1024) NOT NULL,
    mobile_count INT NOT NULL DEFAULT 0,
    content_summary VARCHAR(256) NOT NULL,
    provider VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    operator_id BIGINT UNSIGNED NULL,
    operator_name VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, sent_at),
    KEY idx_status_time (status, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：短信记录（挂在「系统管理」1002 下，sort_no=102 紧随导入中心）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1092, 0, 1002, 'system:sms-record', '短信记录', 'MENU', '/system/sms-record', 'system/SmsLog', 'system:sms-record:read', 'Message', 1, 0, 102, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1092;

-- ############################################################
-- 来源: V38__system_parameters.sql
-- ############################################################

-- =====================================================================================
-- V38 系统参数表扩展 + 硬编码配置值沉淀
-- -------------------------------------------------------------------------------------
-- 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）
-- 目的：
--   1) 为已存在的系统参数表 sys_config 增加两个缺失字段：
--        default_value  —— 参数默认值（工厂默认值，区别于当前生效值 config_value）
--        editable       —— 是否可编辑（1=运行时可改 / 0=系统内置不可改）
--      使表结构完整覆盖「参数编码 / 参数名称 / 参数值 / 默认值 / 描述 / 是否可编辑」。
--   2) 将原散落在 Java @ConfigurationProperties / 规则类中的硬编码配置值
--      （业务阈值、接口地址、开关标志、固定常量）沉淀为可配置参数种子，
--      以便运维在 /api/system/configs 界面或数据库直接调参，无需改代码发版。
--
-- 幂等说明（重要）：
--   - 建表用 CREATE TABLE IF NOT EXISTS（已存在则跳过，便于独立部署场景直接执行）；
--   - 加列用 information_schema 守卫做幂等 ALTER（重复执行不报 1060）；
--   - 种子用 INSERT IGNORE（重复执行不报 1062，且不与 V32 既有 1/2/3 行冲突）。
--   可直接在目标库重复执行。
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1) 完整建表语句（参考 / 独立部署用）：与 V32 列定义严格一致，并补齐 default_value / editable
--    常规 Flyway 流程下该语句为 no-op（sys_config 已由 V32 创建）。
-- -------------------------------------------------------------------------------------
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
    default_value VARCHAR(500) NULL COMMENT '参数默认值（工厂默认值，区别于当前生效值 config_value）',
    editable TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否可编辑：1=运行时可改，0=系统内置不可改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config_key (tenant_id, config_key),
    KEY idx_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------------------------------
-- 2) 幂等扩展：为既有 sys_config 补列（仅当列不存在时才加）
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_config` ADD COLUMN `default_value` VARCHAR(500) NULL COMMENT ''参数默认值（工厂默认值，区别于当前生效值 config_value）''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config' AND COLUMN_NAME = 'default_value'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_config` ADD COLUMN `editable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''是否可编辑：1=运行时可改，0=系统内置不可改''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config' AND COLUMN_NAME = 'editable'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------
-- 3) 种子数据：将原硬编码配置值写入参数表
--    列序：id, tenant_id, config_key(参数编码), config_name(参数名称),
--          config_value(参数值), default_value(默认值), config_type, remark(描述),
--          editable(是否可编辑), status, created_at, updated_at
--    editable=0 的参数属身份/算法/密钥源等结构性配置，不建议运行时随意修改。
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO sys_config (id, tenant_id, config_key, config_name, config_value, default_value, config_type, remark, editable, status, created_at, updated_at)
VALUES
    -- ===== 安全 / JWT =====
    (4, 0, 'security.jwt.issuer', 'JWT 签发者(iss)', 'java-admin-framework', 'java-admin-framework', 'STRING', 'JWT issuer 声明，身份标识，通常不改', 0, 'ACTIVE', NOW(3), NOW(3)),
    (5, 0, 'security.jwt.audience', 'JWT 受众(aud)', 'admin-api', 'admin-api', 'STRING', 'JWT audience 声明，身份标识，通常不改', 0, 'ACTIVE', NOW(3), NOW(3)),
    (6, 0, 'security.jwt.access-token-ttl', '访问令牌有效期', '30m', '30m', 'STRING', 'access_token 过期时长，建议 15~60m', 1, 'ACTIVE', NOW(3), NOW(3)),
    (7, 0, 'security.jwt.refresh-token-ttl', '刷新令牌有效期', '30d', '30d', 'STRING', 'refresh_token 过期时长', 1, 'ACTIVE', NOW(3), NOW(3)),
    (8, 0, 'security.jwt.max-login-failures', '最大登录失败次数', '5', '5', 'INT', '超过则锁定账户', 1, 'ACTIVE', NOW(3), NOW(3)),
    (9, 0, 'security.jwt.lock-duration', '账户锁定时长', '15m', '15m', 'STRING', '渐进式锁定起算时长', 1, 'ACTIVE', NOW(3), NOW(3)),
    (10, 0, 'security.jwt.lock-duration-max', '锁定封顶时长', '24h', '24h', 'STRING', '渐进式锁定时长上限', 1, 'ACTIVE', NOW(3), NOW(3)),
    (11, 0, 'security.jwt.password-history-limit', '密码历史保留条数', '5', '5', 'INT', '禁止复用最近 N 次密码', 1, 'ACTIVE', NOW(3), NOW(3)),
    (12, 0, 'security.jwt.max-concurrent-sessions', '最大并发会话数', '3', '3', 'INT', '同账号最大在线设备/会话，0=不限制', 1, 'ACTIVE', NOW(3), NOW(3)),
    (13, 0, 'security.jwt.session-eviction-strategy', '会话挤占策略', 'evict_oldest', 'evict_oldest', 'STRING', 'reject=拒绝新登录; evict_oldest=淘汰最早会话', 1, 'ACTIVE', NOW(3), NOW(3)),
    (14, 0, 'security.jwt.session-max-inactive', '会话最大空闲时长', '30m', '30m', 'STRING', '刷新令牌空闲超此值被回收', 1, 'ACTIVE', NOW(3), NOW(3)),
    (15, 0, 'security.jwt.session-cleanup-enabled', '会话空闲回收开关', 'true', 'true', 'BOOLEAN', '是否启用空闲回收定时任务', 1, 'ACTIVE', NOW(3), NOW(3)),
    (16, 0, 'security.jwt.session-cleanup-interval-ms', '会话回收间隔(ms)', '300000', '300000', 'INT', '空闲回收定时任务执行间隔', 1, 'ACTIVE', NOW(3), NOW(3)),
    (17, 0, 'security.jwt.online-session-idle-threshold', '在线会话空闲阈值', '15m', '15m', 'STRING', '超过标记为「空闲」(仅展示)', 1, 'ACTIVE', NOW(3), NOW(3)),
    (18, 0, 'security.jwt.online-session-heartbeat-interval', '会话心跳间隔', '60s', '60s', 'STRING', '心跳写库节流间隔', 1, 'ACTIVE', NOW(3), NOW(3)),
    (19, 0, 'security.jwt.online-session-restriction-scan-interval-ms', '登录限制扫描间隔(ms)', '60000', '60000', 'INT', '锁定到期自动解除扫描间隔', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 缓存 =====
    (20, 0, 'cache.ttl', '缓存默认 TTL', '30m', '30m', 'STRING', '@Cacheable 未显式指定时生效', 1, 'ACTIVE', NOW(3), NOW(3)),
    (21, 0, 'cache.key-prefix', '缓存 Key 前缀', 'acme:cache:', 'acme:cache:', 'STRING', 'Redis key 前缀，便于运维排查与批量清理', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 导入 / 导出 =====
    (22, 0, 'export.max-rows', '单次导出最大行数', '100000', '100000', 'INT', '不含表头，超出则拒绝', 1, 'ACTIVE', NOW(3), NOW(3)),
    (23, 0, 'import.max-rows', '单次导入最大行数', '5000', '5000', 'INT', '不含表头，超出则拒绝', 1, 'ACTIVE', NOW(3), NOW(3)),
    (24, 0, 'import.max-errors', '导入回传最大错误数', '200', '200', 'INT', '超出的失败行仅计入统计不回传明细', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 通知渠道 =====
    (25, 0, 'notification.sms.enabled', '短信渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发与费用', 1, 'ACTIVE', NOW(3), NOW(3)),
    (26, 0, 'notification.sms.provider', '短信网关实现', 'LOG', 'LOG', 'STRING', 'LOG=仅记录脱敏日志; HTTP=通用网关', 1, 'ACTIVE', NOW(3), NOW(3)),
    (27, 0, 'notification.sms.timeout-ms', '短信读写超时(ms)', '5000', '5000', 'INT', 'HTTP 模式读写超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (28, 0, 'notification.sms.connect-timeout-ms', '短信连接超时(ms)', '2000', '2000', 'INT', 'HTTP 模式连接超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (29, 0, 'notification.email.enabled', '邮件渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发', 1, 'ACTIVE', NOW(3), NOW(3)),
    (30, 0, 'notification.email.host', 'SMTP 主机', 'localhost', 'localhost', 'STRING', 'SMTP 服务器地址', 1, 'ACTIVE', NOW(3), NOW(3)),
    (31, 0, 'notification.email.port', 'SMTP 端口', '25', '25', 'INT', '25/465/587 依服务商', 1, 'ACTIVE', NOW(3), NOW(3)),
    (32, 0, 'notification.email.starttls', 'SMTP 启用 STARTTLS', 'false', 'false', 'BOOLEAN', '587 通常为 true', 1, 'ACTIVE', NOW(3), NOW(3)),
    (33, 0, 'notification.email.timeout-ms', '邮件读写超时(ms)', '5000', '5000', 'INT', '读写超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (34, 0, 'notification.webhook.enabled', 'Webhook 渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发', 1, 'ACTIVE', NOW(3), NOW(3)),
    (35, 0, 'notification.webhook.timeout-ms', 'Webhook 超时(ms)', '5000', '5000', 'INT', '读写超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (36, 0, 'notification.webhook.connect-timeout-ms', 'Webhook 连接超时(ms)', '2000', '2000', 'INT', '连接超时', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 验证码 / 防重放 / 客户端 IP =====
    (37, 0, 'captcha.expire-seconds', '验证码有效期(s)', '120', '120', 'INT', '验证码超时时长', 1, 'ACTIVE', NOW(3), NOW(3)),
    (38, 0, 'captcha.max-number', '加减法操作数上界', '20', '20', 'INT', '加法/减法操作数 a,b ∈ [1,maxNumber]', 1, 'ACTIVE', NOW(3), NOW(3)),
    (39, 0, 'captcha.mul-max-number', '乘除法操作数上界', '10', '10', 'INT', '乘法因子/除法除数与商上界', 1, 'ACTIVE', NOW(3), NOW(3)),
    (40, 0, 'antireplay.max-clock-skew-ms', '防重放时钟偏差(ms)', '5000', '5000', 'INT', '允许的服务端/客户端时钟偏差上限', 1, 'ACTIVE', NOW(3), NOW(3)),
    (41, 0, 'antireplay.nonce-ttl-seconds', '防重放 nonce 存活(s)', '60', '60', 'INT', '超时后允许同 nonce 再次使用', 1, 'ACTIVE', NOW(3), NOW(3)),
    (42, 0, 'antireplay.max-nonce-cache-size', '防重放 nonce 缓存上限', '100000', '100000', 'INT', '超出后按过期时间惰性清理', 1, 'ACTIVE', NOW(3), NOW(3)),
    (43, 0, 'client-ip.trusted-proxy-count', '可信代理跳数', '1', '1', 'INT', '解析 X-Forwarded-For 跳数，0=直连公网', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 可观测性 / 密钥源 =====
    (44, 0, 'observability.otlp.enabled', 'OTLP 导出开关', 'false', 'false', 'BOOLEAN', '需配置 Collector 后开启', 1, 'ACTIVE', NOW(3), NOW(3)),
    (45, 0, 'observability.otlp.endpoint', 'OTLP traces 端点', 'http://localhost:4318/v1/traces', 'http://localhost:4318/v1/traces', 'STRING', 'Collector 接收地址', 1, 'ACTIVE', NOW(3), NOW(3)),
    (46, 0, 'observability.otlp.timeout-ms', 'OTLP 导出超时(ms)', '2000', '2000', 'INT', '导出 HTTP 超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (47, 0, 'secret.provider', '密钥源类型', 'local', 'local', 'STRING', 'local=环境变量/随机临时; vault=HashiCorp Vault', 0, 'ACTIVE', NOW(3), NOW(3)),
    (48, 0, 'secret.vault.address', 'Vault 地址', 'http://localhost:8200', 'http://localhost:8200', 'STRING', 'Vault 服务地址', 0, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 限流默认 =====
    (49, 0, 'ratelimit.window-seconds', '限流时间窗(s)', '60', '60', 'INT', '默认限流窗口', 1, 'ACTIVE', NOW(3), NOW(3)),
    (50, 0, 'ratelimit.config-update.max', '配置更新限流上限', '10', '10', 'INT', '配置更新接口每分钟上限', 1, 'ACTIVE', NOW(3), NOW(3)),
    (51, 0, 'ratelimit.file.max', '文件接口限流上限', '20', '20', 'INT', '文件接口每分钟上限', 1, 'ACTIVE', NOW(3), NOW(3)),
    (52, 0, 'ratelimit.import.max', '导入接口限流上限', '10', '10', 'INT', '导入接口每分钟上限', 1, 'ACTIVE', NOW(3), NOW(3));

-- ############################################################
-- 来源: V39__observability_center.sql
-- ############################################################

-- V39 可观测中心菜单种子 + 导入中心菜单 ID 碰撞修复。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:observability:read / system:kafka:read / system:idempotency:read 等）
-- 不在此手工登记，由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- =============================================================
-- 1) 历史缺陷修复：V35 与 V36 菜单 ID 碰撞（1091）
--
-- V35__post_management.sql 以 id=1091 插入「岗位管理」（menu_code='system:post'），
-- V36__import_center.sql 同样以 id=1091 插入「导入中心」（menu_code='system:import'）。
-- sys_menu 主键为 id，两条迁移均用 INSERT IGNORE：Flyway 顺序执行下 V36 的插入
-- 因主键冲突被静默丢弃——全新部署中「导入中心」菜单及其 SUPER_ADMIN 绑定缺失，
-- 路由守卫按菜单树鉴权会将 /system/import 重定向到工作台，页面不可达。
--
-- 已应用的迁移不可修改（Flyway checksum 校验会失败），故在本迁移中修复：
-- 以未占用的新 id=1093 重新播种「导入中心」。uk_tenant_menu_code (tenant_id, menu_code)
-- 唯一键保证幂等：若目标环境已手工补过该菜单，INSERT IGNORE 自然跳过，不会产生重复行。
-- =============================================================
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1093, 0, 1002, 'system:import', '导入中心', 'MENU', '/system/import', 'system/ImportCenter', 'system:import:read', 'Upload', 1, 0, 101, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, id FROM sys_menu WHERE menu_code = 'system:import';

-- =============================================================
-- 2) 新增：可观测中心（运维面统一入口，前端 ObservabilityCenterView 五 Tab）
--
-- 路由 /system/observability；页面级权限取 system:monitor:read（监控域基础权限，
-- 与「系统监控」同权，覆盖 N+1 疑似与告警收件箱两个 Tab）；链路追踪 / Kafka 监控 /
-- 幂等记录三个 Tab 由后端按 system:observability:read / system:kafka:read /
-- system:idempotency:read 独立鉴权，前端对 403 展示降级空态（最小授权，不越权放行）。
-- =============================================================
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1094, 0, 1002, 'system:observability-center', '可观测中心', 'MENU', '/system/observability', 'system/ObservabilityCenter', 'system:monitor:read', 'DataLine', 1, 0, 103, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1094;

-- ############################################################
-- 来源: V40__announcement_read_receipt.sql
-- ############################################################

-- =====================================================================================
-- V40 公告已读回执中心（Announcement Read Receipt Center）
-- -------------------------------------------------------------------------------------
-- 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）
--
-- 背景与模型取舍：
--   站内信（V8）采用「发送即 eager 全员投递」：一条 sys_message 配 N 条
--   sys_message_receipt，已读/未读天然可枚举。公告的受众却是「全体活跃用户」，
--   且阅读者通常远小于受众——若沿用 eager 模型，每发布一条公告就要为全量用户
--   预插回执，发布动作写放大（读多写少场景下绝大多数行永远不会被读到）。
--
--   故公告回执采用「懒插入（lazy-insert）」模型：
--     - 回执行只在用户确认已读（markRead）时才写入，行数 ≈ 实际阅读人数；
--     - 「谁还没读」不再由回执表反推，而是用反连接得出：
--       活跃用户集合 − 已读回执集合（见 AnnouncementReadService.unreadUsers）；
--     - 分母问题（已读率 = 已读人数 / 受众数）由 sys_announcement.audience_count
--       解决：发布时快照当时活跃用户数，作为统计分母；V40 之前的存量公告
--       该列为 0，前端统计按 readCount 兜底展示，不除零。
--
-- 幂等设计：
--     - uk_ann_user (tenant_id, announcement_id, user_id) 唯一键保证同一用户对
--       同一公告只有一条回执，markRead 并发/重放不会产生重复行；
--     - sys_announcement.read_count 由「插入成功」事件驱动 +1，与回执行一一对应；
--     - 建表用 CREATE TABLE IF NOT EXISTS；加列用 information_schema 守卫做幂等
--       ALTER（重复执行不报 1060）。可直接在目标库重复执行。
--
-- 接口资源（system:announcement:read-receipt / system:announcement:remind）
-- 不在此手工登记，由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize
-- 自动落库并授予 SUPER_ADMIN；前端入口内嵌于既有公告管理页（无新菜单）。
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1) 表：sys_announcement_read（公告已读回执，懒插入）
--    列名与 com.acme.scaffold.jooq.dsl.JooqTables.SYS_ANNOUNCEMENT_READ 注册严格一致。
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_announcement_read (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    announcement_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    -- 已读回执时间（markRead 时刻）
    read_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- 幂等锚点：同一用户对同一公告只留一条回执；markRead 并发场景下
    -- 先查后插若被唯一键拒绝（DataIntegrityViolationException），按已读幂等处理
    UNIQUE KEY uk_ann_user (tenant_id, announcement_id, user_id),
    -- 「我的已读公告」按用户反查；公告维度统计按 (announcement_id) 走 uk 前缀
    KEY idx_user (tenant_id, user_id),
    KEY idx_ann (tenant_id, announcement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------------------------------
-- 2) 幂等扩展：为既有 sys_announcement 补两列（仅当列不存在时才加）
--     audience_count：发布时活跃用户快照（统计分母）
--     read_count   ：已读人数（markRead 成功时 +1）
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_announcement` ADD COLUMN `audience_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''发布时活跃用户快照（已读率分母）''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_announcement' AND COLUMN_NAME = 'audience_count'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_announcement` ADD COLUMN `read_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''已读人数（markRead 成功时 +1）''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_announcement' AND COLUMN_NAME = 'read_count'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ############################################################
-- 来源: V41__notification_template.sql
-- ############################################################

-- V41 通知模板中心（M10）：为 M6 多渠道通知提供可复用模板样板。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:notification-template:read / system:notification-template:manage）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。

-- 1) 表：sys_notification_template（逻辑删除 + 启用开关，编码租户内唯一）
CREATE TABLE IF NOT EXISTS sys_notification_template (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(100) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    title VARCHAR(200) NULL,
    content TEXT NOT NULL,
    msg_type VARCHAR(16) NOT NULL DEFAULT 'NOTICE',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(255) NULL,
    version INT NOT NULL DEFAULT 0,
    deleted INT NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, template_code),
    KEY idx_tenant_channel (tenant_id, channel),
    KEY idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：通知模板（挂在「系统管理」1002 下，sort_no=104 紧随可观测中心）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1095, 0, 1002, 'system:notification-template', '通知模板', 'MENU', '/system/notification-templates', 'system/NotificationTemplate', 'system:notification-template:read', 'Bell', 1, 0, 104, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1095;

-- ############################################################
-- 来源: V42__notification_record.sql
-- ############################################################

-- V42 通知发送记录（系统管理 → 通知记录），补全 M12：全五渠道统一发送审计面。
-- 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
-- 接口资源（system:notification-record:read）不在此手工登记，
-- 由 ApiResourceAutoSyncRunner 在启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--
-- 口径说明（与 sys_sms_record 的分工）：
--   本表记「每次 NotificationService.dispatch 尝试」（含被拒与失败，覆盖 IN_APP/WEBHOOK/
--   WEBSOCKET/EMAIL/SMS 全渠道，是统一审计面，M11 模板发送回填 template_code）；
--   sys_sms_record 保持「仅校验后实际尝试的网关投递」（网关账单对账面）。
--   短信一次发送双写两表，属刻意设计而非重复。

-- 1) 表：sys_notification_record（摘要 + 掩码存储：标题/正文摘要化、接收方按渠道掩码，PII 不明文落库）
CREATE TABLE IF NOT EXISTS sys_notification_record (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    channel VARCHAR(16) NOT NULL,
    template_code VARCHAR(64) NULL,
    msg_type VARCHAR(32) NULL,
    title_summary VARCHAR(256) NOT NULL,
    content_summary VARCHAR(512) NOT NULL,
    receiver_summary VARCHAR(512) NOT NULL,
    receiver_count INT NOT NULL DEFAULT 0,
    delivered INT NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL,
    error_message VARCHAR(512) NULL,
    operator_id BIGINT UNSIGNED NULL,
    operator_name VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, sent_at),
    KEY idx_channel_time (channel, sent_at),
    KEY idx_status_time (status, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：通知记录（挂在「系统管理」1002 下，sort_no=105 紧随通知模板）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1096, 0, 1002, 'system:notification-record', '通知记录', 'MENU', '/system/notification-records', 'system/NotificationRecord', 'system:notification-record:read', 'Tickets', 1, 0, 105, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1096;

-- ############################################################
-- 来源: V43__notification_preference.sql
-- ############################################################

-- V43 通知偏好中心（M13）：sys_notification_preference，让 M6 多渠道通知首次具备
-- 「谁想收什么」的接收侧口径，并修复 sys_user 三个 notify_* 渠道总开关为「死开关」的缺陷
-- （此前唯一读取方是个人中心自身，发送链路从不读取，用户关闭站内信通知仍照收）。
--
-- 设计要点：
--   1) 懒行模型：无行 = 默认开通，由 sys_user 渠道级总开关（notify_site_message /
--      notify_email / notify_mobile）兜底；恢复默认即物理删行，故无 deleted 列。
--   2) 唯一键 uk_user_channel_type：同一用户同一 (渠道, 消息类型) 至多一行，
--      写入走「查现存 → 更新或插入」，唯一索引兜底并发（与幂等记录表同范式）。
--   3) 渠道白名单 IN_APP / EMAIL / SMS / WEBSOCKET：WEBHOOK 的地址属外部系统，
--      无个人偏好语义，拒绝建行（规则见 NotificationPreferenceRules）。
--   4) 自服务页面（个人中心 → 通知订阅）：无菜单、无权限码播种，
--      接口 /api/notification-preferences/mine 仅登录可访问，userId 一律服务端取自安全上下文。

CREATE TABLE IF NOT EXISTS sys_notification_preference (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(16) NOT NULL,
    msg_type VARCHAR(32) NOT NULL DEFAULT 'NOTICE',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_channel_type (user_id, channel, msg_type),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ############################################################
-- 来源: V44__notification_quiet_hours.sql
-- ############################################################

-- V44 通知免打扰时段（M14）：sys_notification_quiet_hours，让 M6 多渠道通知首次具备
-- 「什么时候收」的时间维口径——M13 偏好中心闭合「谁想收什么」，本模块补齐接收侧最后一维。
--
-- 设计要点：
--   1) 懒行模型：无行 = 无免打扰（与 M13「无行默认开通」互为镜像）；关停免打扰保留配置行
--      （enabled=0），故无「恢复默认」删行语义，也无 deleted 列。
--   2) 按接收者维度、不按渠道：免打扰是时间维总闸（先于 M13 偏好过滤生效），
--      一名用户至多一行，唯一键 uk_user 兜底幂等，写入走「查现存 → 更新或插入」。
--   3) 时间口径：start_time / end_time 为 TIME 列，半开区间 [start, end)，按服务器本地时间
--      裁决；start > end 表达跨午夜窗口（默认 22:00–08:00）；start = end 为退化窗口，
--      由服务端校验拒绝（见 NotificationQuietHoursService.update）。
--   4) 告警穿透：alert_breakthrough 默认 1——ALERT 类型消息在免打扰窗口内照常投递，
--      故障告警不应被时间窗吞掉；其余消息类型（NOTICE/SYSTEM/CC）受抑制。
--   5) 广播（IN_APP 仅 roleIds/orgIds、WEBSOCKET 无定向接收者）与 WEBHOOK 不过滤：
--      受众规模不定且无个人作息语义，属文档化限制（见开发手册 M14 章节）。
--   6) 自服务页面（个人中心 → 免打扰）：无菜单、无权限码播种，
--      接口 /api/notification-quiet-hours/mine 仅登录可访问，userId 一律服务端取自安全上下文。

CREATE TABLE IF NOT EXISTS sys_notification_quiet_hours (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    alert_breakthrough TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user (user_id),
    KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ############################################################
-- 来源: V45__notification_digest.sql
-- ############################################################

-- V45 通知聚合摘要（M17）：sys_notification_digest，让 M6 多渠道通知具备「频次治理」能力——
-- 低价值高频通知（NOTICE 类）先聚合、按阈值或最大等待时长合并为一条摘要再投递，
-- 与 M13（谁想收什么）、M14（什么时候收）共同构成接收侧完整口径。
--
-- 设计要点：
--   1) 一次性投递语义：行无论摘要投递成功/跳过/失败均<b>物理删除</b>（无 status、
--      无 deleted 列）——聚合行是「待投递缓冲」而非档案，反复重投只会制造 FAILED
--      日志轰炸；审计由 M12 sys_notification_record 承担（摘要消息本身也走 dispatch 留痕）。
--   2) 唯一键 uk_user_channel_type：同一用户同一 (渠道, 消息类型) 至多一条聚合行，
--      新消息到达即合并（item_count 累加、items_json 保留最近 maxItems 条、
--      last_at 前推），写入走「查现存 → 更新或插入」（本仓动态 DSL 无 upsert API），
--      与 M13/M14 同范式。
--   3) 汇总触发双条件（先到先投）：item_count 达 app.notifications.digest.threshold
--      立即在挂点内刷新投递；或 last_at 距今超过 max-wait-minutes 由定时任务兜底
--      （idx_last_at 支撑扫描），防止低流量用户摘要永远囤在缓冲区。
--   4) 按接收者维度、不按广播：仅聚合有明确 userId 的定向消息（IN_APP/WEBSOCKET 的
--      receiverIds、EMAIL/SMS 反查 sys_user 全部命中的地址）；广播与 WEBHOOK 无个人
--      投递箱语义，不聚合（与 M13/M14 广播不过滤口径一致）。
--   5) items_json 为 JSON 字符串数组（每条 = 标题优先、正文回退的单行摘要，截断），
--      由服务端序列化写入；投递时按 userId 现查 email/mobile 生成摘要消息，
--      用户已删或无地址则直接删行（无处可投）。
--   6) 摘要消息 msgType 恒为 "DIGEST"：不在 app.notifications.digest.msg-types 配置内
--      （不会再被挂起，防递归），也不在 M13 偏好白名单内（无偏好行、默认开通，
--      仅受 sys_user 渠道总开关约束），天然复用 M13/M14/M16 全链路。

CREATE TABLE IF NOT EXISTS sys_notification_digest (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    channel VARCHAR(16) NOT NULL,
    msg_type VARCHAR(32) NOT NULL,
    item_count INT NOT NULL DEFAULT 0,
    items_json TEXT NULL,
    first_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_channel_type (user_id, channel, msg_type),
    KEY idx_tenant_user (tenant_id, user_id),
    KEY idx_last_at (last_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ############################################################
-- 来源: V46__mask_strategy_center.sql
-- ############################################################

-- V46 脱敏策略中心（M21）：sys_mask_strategy，把「哪些逻辑字段用哪种脱敏类型、对哪些角色豁免」
-- 从散落各处的硬编码收口为可配置策略中心，统一经 MaskStrategyResolver 委托 SensitiveMasker 分派。
--
-- 设计要点：
--   1) 默认安全回落：未命中任何策略的字段，由调用方声明的 SensitiveType 驱动默认掩码
--      （与既有视图层脱敏口径一致），绝不因「未配置策略」而泄漏明文。
--   2) apply_scope：ALL=一律脱敏；ROLE_EXEMPT=命中 exempt_roles 的角色看明文、其余脱敏；
--      NONE=永不脱敏（显式放行，用于已脱敏存储字段）。sensitive_type 驱动 SensitiveMasker 算法。
--   3) 解析/命中/豁免判定集中在 system.maskstrategy.rules.MaskStrategyRules（纯函数），
--      实际脱敏委托 common.sensitive.SensitiveMasker，与 M1 审计落库、M20 审计增强共用同一脱敏内核。
--   4) 接口资源（system:mask-strategy:read / :manage）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   5) 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。

-- 1) 表：sys_mask_strategy（逻辑删除 + 乐观锁；(tenant_id, strategy_code) 唯一约束兜底并发写入）
CREATE TABLE IF NOT EXISTS sys_mask_strategy (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    strategy_code VARCHAR(64) NOT NULL,
    strategy_name VARCHAR(128) NOT NULL,
    field_key VARCHAR(128) NOT NULL,
    sensitive_type VARCHAR(32) NOT NULL,
    apply_scope VARCHAR(16) NOT NULL DEFAULT 'ROLE_EXEMPT',
    exempt_roles VARCHAR(512) NULL,
    priority INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, strategy_code),
    KEY idx_tenant_field (tenant_id, field_key),
    KEY idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：脱敏策略中心（挂在「系统管理」1002 下，sort_no=110 紧随可观测/导出/导入中心之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1120, 0, 1002, 'system:mask-strategy', '脱敏策略中心', 'MENU', '/system/mask-strategies', 'system/MaskStrategy', 'system:mask-strategy:read', 'Lock', 1, 0, 110, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1120;

-- ############################################################
-- 来源: V47__form_pilot.sql
-- ############################################################

-- V47 低代码表单试点（M22 spike）：sys_form_definition，以 JSON Schema 描述表单（字段/校验/布局），
-- 前端按 schema 零依赖渲染，后端按 schema 结构校验后经 /schema/{code} 下发。
--
-- 设计要点（spike 范围，严格收口）：
--   1) 仅交付「契约 + 渲染原型 + 1 个试点表单」：本迁移只建表并播种试点表单 contact-feedback；
--      **不含**提交落库与绑定目标实现——决策点①（绑定既有表 vs 独立 form_submission 表）
--      与决策点②（是否引轻量 schema 库）留待正式立项评审，本期一律按「零依赖手写渲染」收敛。
--   2) target_type 为占位列：NONE（默认，spike 状态）/ BUSINESS_TABLE / FORM_SUBMISSION，
--      取值为后续决策预留，当前读写均不依赖其具体语义。
--   3) schema_json 结构校验集中在 system.form.rules.FormSchemaRules（纯函数）：
--      version + fields[]，字段 key（驼峰标识符）/label/type（白名单 INPUT/TEXTAREA/NUMBER/
--      SELECT/RADIO/SWITCH/DATE）/required/options（SELECT/RADIO 必填非空）/长度范围。
--   4) 接口资源（system:form-definition:read）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   5) 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。

-- 1) 表：sys_form_definition（逻辑删除 + 乐观锁；(tenant_id, form_code) 唯一约束兜底并发写入）
CREATE TABLE IF NOT EXISTS sys_form_definition (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    form_code VARCHAR(64) NOT NULL,
    form_name VARCHAR(128) NOT NULL,
    schema_json JSON NOT NULL,
    target_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_id, form_code),
    KEY idx_tenant_enabled (tenant_id, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 试点表单：contact-feedback（联系反馈）——覆盖 INPUT/TEXTAREA/SELECT/SWITCH/DATE 五类控件渲染
INSERT IGNORE INTO sys_form_definition (id, tenant_id, form_code, form_name, schema_json, target_type, enabled, remark, version, deleted, created_at, updated_at)
VALUES (1, 0, 'contact-feedback', '联系反馈',
        JSON_OBJECT(
            'version', 1,
            'title', '联系反馈',
            'fields', JSON_ARRAY(
                JSON_OBJECT('key', 'name', 'label', '姓名', 'type', 'INPUT', 'required', true, 'maxLength', 32, 'placeholder', '请输入姓名'),
                JSON_OBJECT('key', 'mobile', 'label', '手机号', 'type', 'INPUT', 'required', true, 'maxLength', 11, 'placeholder', '11 位手机号'),
                JSON_OBJECT('key', 'channel', 'label', '反馈渠道', 'type', 'SELECT', 'required', true,
                            'options', JSON_ARRAY(
                                JSON_OBJECT('label', '电话', 'value', 'PHONE'),
                                JSON_OBJECT('label', '邮件', 'value', 'EMAIL'),
                                JSON_OBJECT('label', '在线客服', 'value', 'IM'))),
                JSON_OBJECT('key', 'urgent', 'label', '是否紧急', 'type', 'SWITCH'),
                JSON_OBJECT('key', 'content', 'label', '反馈内容', 'type', 'TEXTAREA', 'required', true,
                            'minLength', 10, 'maxLength', 500, 'rows', 4, 'placeholder', '请详细描述您的问题（至少 10 字）'),
                JSON_OBJECT('key', 'followUpAt', 'label', '期望回访日期', 'type', 'DATE')
            )
        ),
        'NONE', 1, 'M22 spike 试点表单：契约 + 渲染验证，不含提交落库（绑定目标决策点待定）', 0, 0, NOW(3), NOW(3));

-- 3) 菜单：低代码表单试点（挂在「系统管理」1002 下，sort_no=111 紧随脱敏策略中心之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1121, 0, 1002, 'system:form-pilot', '低代码表单试点', 'MENU', '/system/form-pilot', 'system/FormPilot', 'system:form-definition:read', 'EditPen', 1, 0, 111, 'ACTIVE', NOW(3), NOW(3));

-- 4) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1121;

-- ############################################################
-- 来源: V48__openapi_client.sql
-- ############################################################

-- V48 API 开放平台客户端注册与密钥治理（M24）：sys_openapi_client，把「外部系统如何接入本平台」
-- 从散落的口头约定收口为可治理的客户端注册表（client_id 唯一 + 密钥哈希存储 + 轮换/停用/过期）。
--
-- 设计要点：
--   1) 立项前离线核验结论（2026-10-25）：离线仓库仅有 spring-security-oauth2-core/jose/
--      resource-server 6.3.3，**不含** spring-security-oauth2-client（RegisteredClient 所在）与
--      spring-security-oauth2-authorization-server（授权服务器所在）——完整 OAuth2 授权服务器
--      （authorization code / token 端点）离线不可建。本期收口为**自包含的客户端注册 + 密钥治理**
--      （自有领域模型 + DelegatingPasswordEncoder 哈希），不引任何新依赖；协议级开放（/oauth2/token
--      等）待联网环境补齐依赖后另行立项。
--   2) 密钥安全：client_secret 仅以 DelegatingPasswordEncoder 哈希落库（与用户口令同机制），
--      明文仅在创建/轮换成功时返回一次（ClientSecretView），任何查询端点不回放明文。
--   3) 密钥治理：rotateSecret 重新生成（SecureRandom 32 字节 → Base64 URL 无填充 43 字符）并
--      刷新 secret_rotated_at；toggle 停用/启用；expired_at 可选过期时间。
--   4) 结构校验集中在 system.openapi.rules.OpenApiClientRules（纯函数）：client_id 格式、
--      scope 解析与格式、状态白名单、过期时间须晚于当前。
--   5) 接口资源（system:openapi-client:read / :manage）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   6) 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。

-- 1) 表：sys_openapi_client（逻辑删除 + 乐观锁；(tenant_id, client_id) 唯一约束兜底并发注册）
CREATE TABLE IF NOT EXISTS sys_openapi_client (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    client_id VARCHAR(64) NOT NULL,
    client_name VARCHAR(128) NOT NULL,
    client_secret_hash VARCHAR(128) NOT NULL,
    scopes VARCHAR(512) NOT NULL DEFAULT 'read',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    secret_rotated_at DATETIME(3) NULL,
    expired_at DATETIME(3) NULL,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_client (tenant_id, client_id),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：开放平台客户端（挂在「系统管理」1002 下，sort_no=112 紧随低代码表单试点之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1122, 0, 1002, 'system:openapi-client', '开放平台客户端', 'MENU', '/system/openapi-clients', 'system/OpenApiClient', 'system:openapi-client:read', 'Key', 1, 0, 112, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1122;

-- ############################################################
-- 来源: V49__workflow_monitor.sql
-- ############################################################

-- V49 流程监控菜单（M23）：零表结构变更——监控数据来自既有 wf_instance_ext / wf_task_ext /
-- wf_approval_record 与 Flowable 引擎查询，本迁移仅播种「流程监控」菜单并绑定 SUPER_ADMIN。
--
-- 设计要点：
--   1) 监控能力（M23，M4 续）：运行实例分页 / 超时任务分页 / 定义版本链 / 统计摘要（只读）
--      + 任务催办（唯一写操作，实时推送 + 最小间隔防轰炸，催办记录复用 wf_approval_record
--      的 action=REMIND，零新表）。
--   2) 菜单挂「工作流」目录（id=1003，来自 data_dml.sql 基础菜单），sort_no=40 紧随工作流设计（30）之后。
--   3) 接口资源（workflow:monitor:read / workflow:task:remind）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   4) 催办最小间隔经 app.workflow.monitor.remind-min-interval-minutes 配置（默认 30 分钟）。

-- 1) 菜单：流程监控
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1123, 0, 1003, 'workflow:monitor', '流程监控', 'MENU', '/workflow/monitor', 'workflow/Monitor', 'workflow:monitor:read', 'DataLine', 1, 0, 40, 'ACTIVE', NOW(3), NOW(3));

-- 2) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1123;

-- ############################################################
-- 来源: V50__file_dedup.sql
-- ############################################################

-- =====================================================================================
-- V50 文件管理增强（File Management Enhancement）
-- -------------------------------------------------------------------------------------
-- 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）
--
-- 背景与模型取舍：
--   sys_file 每上传一个文件就落一条记录 + 一份物理文件，常见管理场景存在三类浪费：
--     a) 重复上传：同一份内容（如公共头像、模板文档）被不同业务/用户反复上传，
--        磁盘与记录双倍膨胀；
--     b) 误删物理文件：两条记录 stored_name 相同时（历史批量导入或复制），删一条
--        会把另一条指向的物理文件一并删掉，下载即 404；
--     c) 缺少统计：管理者看不到「哪个来源模块传了多少文件、占用多少空间」。
--
--   故本迁移为 sys_file 增加 content_hash 列，支撑三项增强：
--     1) 秒传（dedup）：上传时计算内容 SHA-256，命中未删记录则复用其
--        stored_name/storage_path，不落盘、不产生新物理文件；
--     2) 引用计数删除保护：删除前按 stored_name 统计未删记录数，>1 时仅逻辑删
--        记录、保留物理文件；
--     3) 统计：按 source_module 分组聚合（无需新列，本迁移只加索引支撑）。
--
-- 幂等设计：
--   - 加列与加索引均用 information_schema 守卫做幂等 ALTER（重复执行不报
--     1060/1061  duplicate 错误），可直接在目标库重复执行；
--   - 存量行 content_hash 为 NULL，不参与秒传去重（NULL 不匹配等值查询），
--     语义安全；后续重新上传同内容时自然回填哈希并纳入去重；
--   - CHAR(64) 固定长度存 SHA-256 十六进制，定长列比较快且与 normalizeHash
--     校验严格对齐。
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1) 幂等扩展：sys_file 增加 content_hash 列（内容 SHA-256 十六进制，秒传去重锚点）
--    位置置于 content_type 之后，与 JooqTables.SYS_FILE 列顺序无关（DSL 按名映射）。
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_file` ADD COLUMN `content_hash` CHAR(64) NULL COMMENT ''内容 SHA-256 十六进制（秒传去重锚点）'' AFTER `content_type`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND COLUMN_NAME = 'content_hash'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------
-- 2) 幂等扩展：content_hash 加普通索引（秒传命中等值查询；NULL 值不进索引，零膨胀）
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_file` ADD KEY `idx_content_hash` (`content_hash`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_file' AND INDEX_NAME = 'idx_content_hash'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ############################################################
-- 来源: V51__tenant_management.sql
-- ############################################################

-- V51 租户管理域（M19 P2）：sys_tenant，把「平台下有哪些租户、各自联系人/状态/有效期」从口头约定
-- 收口为可治理的租户注册表（tenant_code 唯一 + 状态机 ENABLED/DISABLED + 可选过期）。
--
-- 设计要点：
--   1) 多租户隔离整体策略（M19）：共享表 + tenant_id 列 + jOOQ 拦截强制隔离（P3 落地）。
--      sys_tenant 本身是「租户维度表」，由平台管理员统一维护，所有行 tenant_id 恒为 0（平台租户），
--      查询不按当前租户过滤——它是隔离的「被治理对象」而非「被隔离数据」。
--   2) 结构校验集中在 common.tenant.TenantRules（纯函数）：tenant_code 格式、tenant_name 长度、
--      状态白名单、联系人电话/邮箱格式、过期时间须晚于当前。
--   3) 接口资源（system:tenant:read / :manage）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   4) 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。
--   5) 幂等：CREATE TABLE IF NOT EXISTS + INSERT IGNORE，可重复执行。

-- 1) 表：sys_tenant（逻辑删除 + 乐观锁；(tenant_code) 唯一约束兜底并发注册）
CREATE TABLE IF NOT EXISTS sys_tenant (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    tenant_code VARCHAR(64) NOT NULL,
    tenant_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(64) NULL,
    contact_phone VARCHAR(32) NULL,
    contact_email VARCHAR(128) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    expire_at DATETIME(3) NULL,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：租户管理（挂在「系统管理」1002 下，sort_no=113 紧随开放平台客户端 112 之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1124, 0, 1002, 'system:tenant', '租户管理', 'MENU', '/system/tenants', 'system/TenantView', 'system:tenant:read', 'OfficeBuilding', 1, 0, 113, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1124;

-- ############################################################
-- 来源: V52__dashboard_menu.sql
-- ############################################################

-- V52 报表大屏菜单（M27 前端配套页）：把「运营总览 + 时间序列趋势」页接入侧边栏与路由守卫。
--
-- 设计要点：
--   1) 后端 /api/system/dashboard/overview 与 /trend 已在 M27 四刀交付，权限 system:dashboard:read
--      由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，此处仅补前端菜单入口。
--   2) 挂在「报表管理」目录(1030)下，sort_no=40 紧随 定时任务执行(10)/登录(20)/接口调用(30) 之后。
--   3) route_path 必须与前端路由（report/dashboard）严格一致，否则路由守卫会重定向到工作台。
--   4) 依赖：V32（报表管理目录 id=1030）、M27（dashboard 接口）。幂等：INSERT IGNORE + NOT EXISTS。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1056, 0, 1030, 'system:dashboard', '报表大屏', 'MENU', '/report/dashboard', 'report/DashboardView', 'system:dashboard:read', 'DataAnalysis', 1, 0, 40, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1056;

-- ############################################################
-- 来源: V53__form_submission.sql
-- ############################################################

-- V53 表单提交落库（M22 正式立项）：sys_form_submission，承接 V47 低代码表单试点的
-- 「提交落库与绑定目标」决策点（target_type=FORM_SUBMISSION），将动态表单提交值
-- 以 JSON 持久化到独立提交表，不侵入业务表、不做运行时 DDL。
--
-- 设计要点：
--   1) 通用提交表：以 (form_definition_id, form_code) 关联表单定义，data_json 存提交值；
--      后端提交前经 FormSubmissionRules 按 schema 字段校验（必填/类型/长度/选项白名单/未知字段拒绝）。
--   2) 沿用全库约定：tenant_id 默认 0（与 FORM_DEFINITION 同口径），逻辑删除 deleted + 乐观锁 version，
--      created_at/updated_at 由 JooqWriters.insert 自动填充。
--   3) data_json 不在此做结构约束（schema 校验在应用层完成），便于后续字段演进。
--   4) 将 V47 试点表单 contact-feedback 的 target_type 由 NONE 置为 FORM_SUBMISSION，
--      使其正式具备提交能力（idempotent UPDATE，重复执行幂等）。
--   5) 接口资源 system:form-submission:create / system:form-submission:read 不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   6) 依赖：V47（sys_form_definition、菜单 id=1121 低代码表单试点）。

-- 1) 表：sys_form_submission（逻辑删除 + 乐观锁；按 (tenant_id, form_code, created_at) 建查询索引）
CREATE TABLE IF NOT EXISTS sys_form_submission (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    form_definition_id BIGINT UNSIGNED NOT NULL,
    form_code VARCHAR(64) NOT NULL,
    submitted_by VARCHAR(64) NULL,
    data_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_tenant_form_created (tenant_id, form_code, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 试点表单绑定目标落库：contact-feedback 由 NONE → FORM_SUBMISSION（幂等 UPDATE）
UPDATE sys_form_definition
SET target_type = 'FORM_SUBMISSION'
WHERE form_code = 'contact-feedback'
  AND target_type = 'NONE';

-- ############################################################
-- 来源: V54__tenant_isolation_menu.sql
-- ############################################################

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

-- ############################################################
-- 来源: V55__realtime_menu.sql
-- ############################################################

-- V55 实时推送管理菜单（M29 只读 + 手动推送运维页）：把后端已交付的 RealtimeController
-- （/api/realtime：sessions / online-count / push/broadcast / push/user）接入侧边栏与路由守卫。
--
-- 设计要点：
--   1) 后端 GET /api/realtime/sessions、/api/realtime/online-count 权限 system:realtime:read；
--      POST /api/realtime/push/broadcast、/api/realtime/push/user 权限 system:realtime:push。
--      权限码由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，
--      此处仅补前端菜单入口（基础 read 权限），与 MonitorView 用 system:monitor:read 覆盖多子权限一致。
--   2) 挂在「系统管理」目录(1002)下，sort_no=115 紧随 租户隔离态势(1125 / 114) 之后。
--   3) route_path 必须与前端路由（system/realtime）严格一致，否则路由守卫会重定向到工作台。
--   4) 依赖：V32（系统管理目录 id=1002）、realtime 模块 RealtimeController（已交付）。
--   5) 幂等：INSERT IGNORE + NOT EXISTS（menu_code 唯一），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1126, 0, 1002, 'system:realtime', '实时推送', 'MENU', '/system/realtime', 'system/RealtimeView', 'system:realtime:read', 'Bell', 1, 0, 115, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （push 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1126;

-- ############################################################
-- 来源: V56__data_scope_menu.sql
-- ############################################################

-- V56 数据权限配置菜单（M31）：把后端 DataScopeResourceRegistry 自动发现的可配置资源，
-- 通过本页以「角色 × 资源」矩阵形式可视化配置。后端仅新增只读发现端点
-- GET /api/system/data-scope/resources（权限 system:data-scope:read，由 ApiResourceAutoSyncRunner
-- 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN）；保存仍复用 RoleController 的
-- PUT /api/system/roles/{id}/data-scopes（权限 system:role:update）。
--
-- 设计要点：
--   1) 资源清单由后端启动时扫描 @DataScope 自动得到，新增受控资源无需改前端常量即出现在本页。
--   2) 挂在「系统管理」目录(1002)下，sort_no=116 紧随 实时推送(1126 / 115) 之后。
--   3) route_path 必须与前端路由（system/data-scope）严格一致，否则路由守卫会重定向到工作台。
--   4) 依赖：V32（系统管理目录 id=1002）、DataScopeResourceRegistry（新增）。
--   5) 幂等：INSERT IGNORE + NOT EXISTS（menu_code 唯一），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1127, 0, 1002, 'system:data-scope', '数据权限', 'MENU', '/system/data-scope', 'system/DataScopeView', 'system:data-scope:read', 'Lock', 1, 0, 116, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1127;

-- ############################################################
-- 来源: V58__form_definition_menu.sql
-- ############################################################

-- V58 表单定义管理菜单（M22 深化）：在「系统管理」(1002) 下新增「表单定义管理」入口，
-- 承载表单定义 CRUD + JSON-Schema 可视化编辑 + 实时预览（复用前端 DynamicForm 组件）。
-- 与既有「低代码表单试点」(id=1121) 互补：本页负责定义生命周期管理，试点页负责运行时
-- 渲染 / 提交 / 提交记录查看。
--
-- 设计要点：
--   1) 后端接口前缀 /api/system/form-definitions；读权限复用 system:form-definition:read
--      （由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN）；
--      写权限 system:form-definition:create / update / delete 同样自动登记。
--   2) route_path 必须与前端路由（system/form-definition）严格一致，否则路由守卫会重定向到工作台。
--   3) 依赖：V47（sys_form_definition、低代码表单试点菜单 1121）、V53（提交落库）。
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1），可重复执行。
--   5) sort_no=117 紧随数据权限(1127/116) 之后，置于系统管理目录末尾。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1128, 0, 1002, 'system:form-definition', '表单定义管理', 'MENU', '/system/form-definition', 'system/FormDefinitionView', 'system:form-definition:read', 'Document', 1, 0, 117, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1128;

-- ############################################################
-- 来源: V59__job_dependency.sql
-- ############################################################

-- V59 任务依赖编排 DAG（M33）：为 sys_job 增加 depends_on 列，声明本任务依赖的前置任务。
--
-- 设计要点：
--   1) 依赖以 JSON 数组存储（如 [101, 102]），零新增表、零新索引负担；依赖的解析与环检测
--      全部在 JobDagRules 纯函数层完成（无 Spring / DB 依赖，可离线单测）。
--   2) WAITING 状态：存在未满足依赖的任务启动时进入 WAITING（不向调度器注册自身触发），
--      待其全部依赖成功执行后由完成事件驱动一次「即时触发」（复用 JobSchedulerPort.scheduleOneTime）。
--   3) 依赖检索走全量解析（任务量级小，无需为 depends_on 建索引），由 JooqJobDagAdapter 完成。
--   4) 幂等：列已存在则跳过（MySQL 8 的 ALTER TABLE ADD COLUMN 不支持 IF NOT EXISTS，故用
--      information_schema 探测后动态执行）。

SET @db = DATABASE();
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_job' AND COLUMN_NAME = 'depends_on');
SET @sql = IF(@col = 0,
    'ALTER TABLE sys_job ADD COLUMN depends_on VARCHAR(1024) DEFAULT NULL COMMENT ''依赖任务ID列表(JSON数组)''',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ############################################################
-- 来源: V60__config_center_wecom_dingtalk_params.sql
-- ############################################################

-- =====================================================================================
-- V60 参数中心补充：企业微信 / 钉钉机器人渠道参数（M35 渠道 + M36 热更新）
-- -------------------------------------------------------------------------------------
-- 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）
-- 目的：将 M35 新增的企微/钉钉机器人渠道开关与默认地址沉淀为可配置参数，
--       使其可经参数中心界面或直连数据库动态调整，并配合 ConfigService 热更新即时生效。
--
-- 幂等说明：种子用 INSERT IGNORE（重复执行不报 1062）。本迁移不改动任何既有迁移文件，
--       仅向 sys_config 追加新行（独立 id 段 9001+，不与 V38 既有 4~52 行冲突）。
--       可直接在目标库重复执行。secret 属凭据，按规范经环境变量注入，不入库。
-- =====================================================================================

INSERT IGNORE INTO sys_config (id, tenant_id, config_key, config_name, config_value, default_value, config_type, remark, editable, status, created_at, updated_at)
VALUES
    -- ===== 企业微信机器人 =====
    (9001, 0, 'notification.wecom.enabled', '企业微信机器人渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发；开后经参数中心/直改库 + refresh 即热更新', 1, 'ACTIVE', NOW(3), NOW(3)),
    (9002, 0, 'notification.wecom.webhook-url', '企业微信机器人默认 Webhook 地址', NULL, NULL, 'STRING', '消息未自带 webhookUrl 时取此默认；通常含 key 参数', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 钉钉机器人 =====
    (9003, 0, 'notification.dingtalk.enabled', '钉钉机器人渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发；开后经参数中心/直改库 + refresh 即热更新', 1, 'ACTIVE', NOW(3), NOW(3)),
    (9004, 0, 'notification.dingtalk.webhook-url', '钉钉机器人默认 Webhook 地址', NULL, NULL, 'STRING', '消息未自带 webhookUrl 时取此默认；通常含 access_token 参数', 1, 'ACTIVE', NOW(3), NOW(3));

-- ############################################################
-- 来源: V61__role_column_permission.sql
-- ############################################################

-- V61 字段级数据权限分配（M41）：sys_role_column_permission，把角色对受控字段的明文可见权
-- 以「角色 × 资源 × 字段」三元组持久化，供统一配置页（PermissionPolicyView 字段权限 Tab）读写。
--
-- 设计要点：
--   1) 与 M31 行级数据权限（sys_role_data_scope）对称：此处为列级（字段级）的「角色 × 资源 × 字段」分配。
--   2) 完整权限码 = resourceCode + ':column:' + field（如 system:user:column:mobile），
--      由 ColumnPermissionRules.columnPermissionCode 派生，与运行时 ColumnPermissionApplier 口径同源。
--   3) 鉴权：读取复用 system:role:read；保存复用 system:role:update（在角色管理上下文中操作）。
--   4) role_id 无物理外键（与 sys_role_data_scope 一致，避免级联误删），由应用层保证存在性。
--   5) 运行时脱敏是否生效由 column-permission.enforce 开关控制（默认关闭，零线上回归，同 M19）。
--   6) 依赖：V32（系统管理目录 id=1002）；ColumnPermissionController 只读资源端点（system:column-permission:read）。

CREATE TABLE IF NOT EXISTS sys_role_column_permission (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    resource_code VARCHAR(128) NOT NULL,
    field VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_resource_field (tenant_id, role_id, resource_code, field),
    KEY idx_role (tenant_id, role_id),
    KEY idx_resource (tenant_id, resource_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 菜单：字段/数据权限统一配置（M41），挂在「系统管理」1002 下，sort_no=117 紧随数据权限(116)之后。
-- route_path 须与前端路由（system/permission-policy）严格一致，否则路由守卫会重定向到工作台。
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1128, 0, 1002, 'system:permission-policy', '字段数据权限', 'MENU', '/system/permission-policy', 'system/PermissionPolicyView', 'system:column-permission:read', 'Lock', 1, 0, 117, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1128;

-- ############################################################
-- 来源: V62__fix_permission_policy_menu.sql
-- ############################################################

-- 修正 M41 字段/数据权限统一配置页菜单缺失（V61 与 V58 误用同一菜单主键 1128）。
-- 受 INSERT IGNORE 主键约束，V61 的 permission-policy 菜单被静默忽略（1128 已被 form-definition 占用），
-- 导致 /system/permission-policy 路由在菜单守卫中不可达。本迁移以独立主键 1130 补种该菜单并绑定 SUPER_ADMIN。
-- 幂等：INSERT IGNORE + SELECT 绑定，可重复执行；表结构（sys_role_column_permission）已在 V61 正常创建，不涉及。
-- 设计要点：
--   1) 与 M31 行级数据权限（sys_role_data_scope）对称：此处为列级（字段级）的「角色 × 资源 × 字段」分配；
--   2) 读权限复用 system:column-permission:read（已由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）；
--   3) route_path 须与前端路由（system/permission-policy）严格一致，否则路由守卫会重定向到工作台。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1130, 0, 1002, 'system:permission-policy', '字段数据权限', 'MENU', '/system/permission-policy', 'system/PermissionPolicyView', 'system:column-permission:read', 'Lock', 1, 0, 118, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1130;

-- ############################################################
-- 来源: V63__health_menu.sql
-- ############################################################

-- M42 健康探针前端可视化页菜单：在「系统管理」(1002) 下新增「健康探针」入口，
-- 承载 /livez、/readyz 两个 k8s 风格根路径探针的可视化（存活/就绪状态 + 明细）。
-- 设计要点：
--   1) 后端探针端点已在 scaffold-monitor 的 ProbeController 提供（permitAll，无需鉴权）；
--   2) 读权限复用 system:monitor:read（由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN），
--      无需新增权限登记（与「系统监控」「缓存监控」「可观测中心」同权）；
--   3) route_path 须与前端路由（system/health）严格一致，否则路由守卫会重定向到工作台；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN，可重复执行；
--   5) vite dev 代理需转发 /livez、/readyz（见 pc-admin-vue3-vite/vite.config.js），
--      生产环境由同源后端直接处理根路径探针。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1129, 0, 1002, 'system:health', '健康探针', 'MENU', '/system/health', 'system/HealthView', 'system:monitor:read', 'Monitor', 1, 0, 119, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1129;

-- ############################################################
-- 来源: V64__oauth2_token_registry.sql
-- ############################################################

-- M43 OAuth2 令牌治理：签发令牌注册表（记录 jti + 元数据，支持查看/吊销，吊销后强制失效）。
-- 与 M38–M40 自包含 RS256 令牌签发互补：此前令牌为无状态 JWT，无法在签发后撤销；
-- 本表把每次签发的 jti 落库，吊销（status=REVOKED）经 OAuth2TokenRevocationFilter 在请求期实时拒绝。
-- 设计要点：
--   1) jti 唯一（uk_jti），与 OAuth2TokenProvider 签发时写入 JWT 的 id 声明严格一致；
--   2) status 仅 ACTIVE / REVOKED；列表查询以 status=ACTIVE 且 expires_at>now 定义「活动」；
--   3) 软删除（deleted）+ 乐观锁（version）与既有表一致；tenant_id 恒为 0（平台维表，由平台统一维护）。

CREATE TABLE IF NOT EXISTS sys_oauth2_token (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    jti VARCHAR(64) NOT NULL,
    client_id VARCHAR(128) NOT NULL,
    user_id BIGINT NULL,
    username VARCHAR(128) NULL,
    scope VARCHAR(512) NULL,
    token_type VARCHAR(20) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_jti (tenant_id, jti),
    KEY idx_client (tenant_id, client_id),
    KEY idx_user (tenant_id, user_id),
    KEY idx_status_exp (tenant_id, status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ############################################################
-- 来源: V65__oauth2_token_menu.sql
-- ############################################################

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

-- ############################################################
-- 来源: V66__form_submission_menu.sql
-- ############################################################

-- V66 表单提交管理菜单（M47 跨表单统一提交管理）：在「系统管理」(1002) 下新增「表单提交管理」入口，
-- 承载跨表单提交记录的统一检索（分页 / 统计 / 详情 / 逻辑删除）与导出中心模板触发。
-- 与既有「低代码表单试点」(id=1121，运行时渲染 / 提交 / 按单表单查记录) 互补：
-- 本页是表单提交数据的治理入口，视角从「单个表单」升级为「全部表单」。
--
-- 设计要点：
--   1) 后端接口前缀 /api/system/form-submissions；读权限复用 system:form-submission:read、
--      删除权限 system:form-submission:delete，均由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize
--      自动落库并授予 SUPER_ADMIN（本菜单仅负责导航可见性与角色绑定）；
--   2) route_path 必须与前端路由（system/form-submissions）严格一致，否则路由守卫会重定向到工作台；
--   3) 依赖：V47（sys_form_definition、低代码表单试点）、V53（sys_form_submission 落库）；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1），可重复执行；
--   5) sort_no=121 紧随 OAuth2 令牌管理(1131/120) 之后，置于系统管理目录末尾。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1132, 0, 1002, 'system:form-submission', '表单提交管理', 'MENU', '/system/form-submissions', 'system/FormSubmissionManageView', 'system:form-submission:read', 'List', 1, 0, 121, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read/delete 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1132;

-- ############################################################
-- 来源: V67__menu_reorganize.sql
-- ############################################################

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

-- ############################################################
-- 来源: V68__oauth2_pkce_consent.sql
-- ############################################################

-- V68 OAuth2 授权码流程强化（V1.0.5 #900）：redirect_uri 白名单 + PKCE 强制开关 + 用户同意(consent)持久化。
--
-- 设计要点：
--   1) sys_openapi_client 增两列：
--        - redirect_uris   ：授权码回调地址白名单（逗号分隔，空白=不约束，兼容存量客户端）。
--        - pkce_required   ：是否强制 PKCE（授权码流程），默认 0（不强制），新注册_public 客户端建议置 1。
--      与 V48 同表，沿用其 BIGINT UNSIGNED / utf8mb4 约定（H2 测试与 MySQL 生产一致）。
--   2) 新增 sys_oauth2_consent：资源拥有者对客户端的授权同意记录。
--        - 一行 = 一次「用户 X 对客户端 C 在 scopes 集合上 APPROVED/DENIED」；
--        - 「记住授权」写 APPROVED 行；授权端点查 (user_id, client_id, scopes⊆已授权) 的未过期 APPROVED 行，
--          命中则直接签发授权码，否则要求用户再次确认（返回 consent_required）。
--        - 无 deleted/version：同意记录采用「追加新行」语义，历史可追溯，无需逻辑删除与乐观锁。
--   3) 依赖：V48（sys_openapi_client 已存在）。本迁移幂等（ALTER 幂等依赖 Flyway 版本控制，重复执行会报已存在列，
--      故仅向前追加重跑；如需本地重置，按 Flyway 标准流程 baseline/repair）。

-- 1) sys_openapi_client 增强列
ALTER TABLE sys_openapi_client
    ADD COLUMN redirect_uris VARCHAR(1024) NULL COMMENT '授权码回调地址白名单（逗号分隔，空白=不约束）';

ALTER TABLE sys_openapi_client
    ADD COLUMN pkce_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否强制 PKCE（授权码流程）';

-- 2) 用户同意记录表
CREATE TABLE IF NOT EXISTS sys_oauth2_consent (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    scopes VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_client (user_id, client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ############################################################
-- 来源: V69__workflow_delegate.sql
-- ############################################################

-- V69 工作流委派（V1.0.5 #901）：wf_task_ext 增加委派发起人与委派状态列，支撑「临时委托办理、办结交还原审批人」。
--
-- 设计要点：
--   1) wf_task_ext 增两列（与既有 wf_* 表 BIGINT UNSIGNED / VARCHAR 约定一致）：
--        - delegator_user_id ：委派发起人（原审批人）。任务被委派给 assignee_user_id 期间记录真正的负责人，
--          被委托人办结交还后 assignee_user_id 还原为该值。
--        - delegation_state  ：委派状态。PENDING=已委派待办、RESOLVED=被委托人已办结交还、NULL=非委派任务。
--   2) 委派/办结交还的写操作复用既有引擎端口 delegate/resolve 与 wf_approval_record（action=DELEGATE/DELEGATE_RESOLVE），
--      零新表、零新接口权限手工登记（由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN）。
--   3) 本迁移向前追加，幂等依赖 Flyway 版本控制；重复执行会因列已存在报错，需按 Flyway 标准流程 baseline/repair。

ALTER TABLE wf_task_ext
    ADD COLUMN delegator_user_id BIGINT UNSIGNED NULL COMMENT '委派发起人（原审批人），非委派任务为 NULL';

ALTER TABLE wf_task_ext
    ADD COLUMN delegation_state VARCHAR(16) NULL COMMENT '委派状态：PENDING=已委派待办、RESOLVED=已办结交还、NULL=非委派';

-- ############################################################
-- 来源: V70__alert_rule.sql
-- ############################################################

-- V70 告警规则闭环（V1.0.5 #922）：新增 alert_rule 表，支撑可配置阈值规则的持久化与周期评估。
--
-- 设计要点：
--   1) alert_rule 独立新表，与既有 wf_*/sys_* 表 BIGINT UNSIGNED / utf8mb4 约定一致。
--   2) 指标键(metric_key)/比较符(comparator)/严重度(severity)/渠道类型(channel_type) 均以
--      字符串存储枚举名（由 AlertRuleRules 解析校验），不动表结构即可横向扩展枚举。
--   3) 逻辑删除(deleted) + 乐观锁(version) + 审计时间(created_at/updated_at) 与仓库其他表一致；
--      created_by/updated_by 允许 NULL（规则可由调度线程创建/更新，无操作人上下文）。
--   4) 零新接口权限手工登记：管理端点 @PreAuthorize 由 ApiResourceAutoSyncRunner 扫描自动落库并授予 SUPER_ADMIN。
--   5) 本迁移向前追加，幂等依赖 Flyway 版本控制；重复执行会因表已存在报错，需按 Flyway 标准流程 baseline/repair。

CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rule_name VARCHAR(128) NOT NULL,
    metric_key VARCHAR(32) NOT NULL,
    comparator VARCHAR(8) NOT NULL,
    threshold DECIMAL(12, 4) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'WARNING',
    for_duration_sec INT NOT NULL DEFAULT 0,
    consecutive_count INT NOT NULL DEFAULT 1,
    channel_type VARCHAR(16) NOT NULL DEFAULT 'INBOX',
    channel_config VARCHAR(1024) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    remark VARCHAR(255) NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_metric_enabled (metric_key, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ############################################################
-- 来源: V71__idempotency_menu.sql
-- ############################################################

-- V71 幂等记录管理菜单（M2 幂等模块运维侧面）：在「系统管理」(parent_id=1002) 下新增「幂等记录」入口，
-- 承载幂等记录分页 / 统计 / 过期清理三接口的前端页面（IdempotencyRecordView）。
-- 设计要点：
--   1) 后端 IdempotencyRecordController（/api/system/idempotency-records），读权限 system:idempotency:read、
--      清理权限 system:idempotency:manage，均经 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN；
--   2) route_path 须与前端路由（system/idempotency）严格一致，否则路由守卫会重定向到工作台；
--   3) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1），可重复执行；sort_no=122 置于 OAuth2 令牌(120) 之后。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1142, 0, 1002, 'system:idempotency', '幂等记录', 'MENU', '/system/idempotency', 'system/IdempotencyRecordView', 'system:idempotency:read', 'Key', 1, 0, 122, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
-- （read 权限由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动授予 SUPER_ADMIN）
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1142;

-- ############################################################
-- 来源: V72__api_docs_menu.sql
-- ############################################################

-- V72 API 文档中心菜单（#972）：复用 Springdoc 标准端点 /v3/api-docs 的纯前端浏览器，
-- 在「系统管理」(parent_id=1002) 下新增「API 文档」入口，承载 ApiDocView。
-- 设计要点：
--   1) 纯前端模块，后端无需新增 Controller；springdoc 已在 scaffold-framework 启用（local 开放、prod 关闭）；
--   2) route_path 须与前端路由（system/api-docs）严格一致，否则路由守卫会重定向到工作台；
--   3) 权限码 system:api-docs:read 预留：当前后端未对 /v3/api-docs 挂独立 @PreAuthorize（local 开放），
--      若后续加鉴权，ApiResourceAutoSyncRunner 会扫描并自动授予 SUPER_ADMIN；菜单此处先绑定 SUPER_ADMIN 保证可见；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1），可重复执行；sort_no=123 置于幂等记录(122) 之后。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1143, 0, 1002, 'system:api-docs', 'API 文档', 'MENU', '/system/api-docs', 'system/ApiDocView', 'system:api-docs:read', 'Document', 1, 0, 123, 'ACTIVE', NOW(3), NOW(3));

-- 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1143;

-- ############################################################
-- 来源: V73__workflow_instance_admin_menu.sql
-- ############################################################

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

-- ############################################################
-- 来源: V74__cms.sql
-- ############################################################

-- V74 内容管理 CMS（ROADMAP R5）：栏目树 + 文章 CRUD + 富文本 + 发布态。
-- 设计要点：
--   1) 标识符全部显式加引号保留 snake_case 小写（H2 元数据库名小写、生产 MySQL 大小写敏感，未引号会坏）；
--   2) 索引用独立 CREATE INDEX 语句（内联 KEY `x`(...) 会被 H2 误解析为 domain）；
--   3) tenant_id 承载租户隔离，enforce 关闭时恒为 0；
--   4) status 用 VARCHAR(20) 承载 DRAFT/PUBLISHED/OFFLINE，由应用层校验；
--   5) 菜单种子：挂载顶层「内容管理」目录（id=9301），下挂栏目管理/文章管理（9302/9303），
--      permission_code 作为菜单可见性判定码；更细的 cms:*:publish 等由 ApiResourceAutoSyncRunner
--      扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，无需手工登记；
--   6) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

CREATE TABLE `cms_category` (
    `id`           BIGINT        NOT NULL,
    `tenant_id`    BIGINT        NOT NULL DEFAULT 0,
    `parent_id`    BIGINT        NULL,
    `category_code` VARCHAR(64)  NOT NULL,
    `category_name` VARCHAR(128) NOT NULL,
    `description`  VARCHAR(512)  NULL,
    `sort_no`      INT           NOT NULL DEFAULT 0,
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    `version`      INT           NOT NULL DEFAULT 0,
    `deleted`      INT           NOT NULL DEFAULT 0,
    `created_by`   VARCHAR(64)   NULL,
    `created_at`   TIMESTAMP     NULL,
    `updated_by`   VARCHAR(64)   NULL,
    `updated_at`   TIMESTAMP     NULL,
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_cms_category_tenant_parent` ON `cms_category` (`tenant_id`, `parent_id`);
CREATE INDEX `idx_cms_category_code`          ON `cms_category` (`tenant_id`, `category_code`);
CREATE INDEX `idx_cms_category_status`        ON `cms_category` (`tenant_id`, `status`);

CREATE TABLE `cms_article` (
    `id`           BIGINT        NOT NULL,
    `tenant_id`    BIGINT        NOT NULL DEFAULT 0,
    `category_id`  BIGINT        NOT NULL,
    `title`        VARCHAR(255)  NOT NULL,
    `summary`      VARCHAR(512)  NULL,
    `content`      TEXT          NULL,
    `author`       VARCHAR(64)   NULL,
    `cover_url`    VARCHAR(512)  NULL,
    `status`       VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    `view_count`   BIGINT        NOT NULL DEFAULT 0,
    `published_at` TIMESTAMP     NULL,
    `offline_at`   TIMESTAMP     NULL,
    `version`      INT           NOT NULL DEFAULT 0,
    `deleted`      INT           NOT NULL DEFAULT 0,
    `created_by`   VARCHAR(64)   NULL,
    `created_at`   TIMESTAMP     NULL,
    `updated_by`   VARCHAR(64)   NULL,
    `updated_at`   TIMESTAMP     NULL,
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_cms_article_tenant_category` ON `cms_article` (`tenant_id`, `category_id`);
CREATE INDEX `idx_cms_article_status`          ON `cms_article` (`tenant_id`, `status`);
CREATE INDEX `idx_cms_article_title`           ON `cms_article` (`tenant_id`, `title`);

-- ---------------- 菜单种子 ----------------

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9301, 0, 0, 'cms', '内容管理', 'CATALOG', NULL, NULL, NULL, 'Document', 1, 0, 80, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9302, 0, 9301, 'cms:category', '栏目管理', 'MENU', '/cms/category', 'cms/CategoryView', 'cms:category:manage', 'FolderOpened', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9303, 0, 9301, 'cms:article', '文章管理', 'MENU', '/cms/article', 'cms/ArticleView', 'cms:article:manage', 'FileText', 1, 0, 20, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9302;
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9303;

-- ############################################################
-- 来源: V75__im.sql
-- ############################################################

-- V75 站内消息 IM（ROADMAP R6）：会话 / 会话成员 / 消息 + 已读回执 + 实时推送解耦。
-- 设计要点：
--   1) 标识符全部显式加引号保留 snake_case 小写（H2 元数据库名小写、生产 MySQL 大小写敏感，未引号会坏）；
--   2) 索引用独立 CREATE INDEX 语句（内联 KEY `x`(...) 会被 H2 误解析为 domain）；
--   3) tenant_id 承载租户隔离，enforce 关闭时恒为 0；
--   4) 会话类型 conversation_type（P2P/GROUP）、消息类型 msg_type（TEXT/IMAGE/...）、状态由应用层校验；
--   5) 已读回执由 im_conversation_member.last_read_message_id 驱动（雪崩 id 单调递增，可比较）；
--   6) 菜单种子：挂载顶层「站内消息」目录（id=9401），下挂会话管理/消息中心（9402/9403），
--      permission_code 作为菜单可见性判定码；更细的 im:*:send 等由 ApiResourceAutoSyncRunner
--      扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN，无需手工登记；
--   7) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

CREATE TABLE `im_conversation` (
    `id`                 BIGINT        NOT NULL,
    `tenant_id`          BIGINT        NOT NULL DEFAULT 0,
    `conversation_type`  VARCHAR(20)   NOT NULL,
    `title`              VARCHAR(255)  NULL,
    `owner_id`           BIGINT        NULL,
    `status`             VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    `version`            INT           NOT NULL DEFAULT 0,
    `deleted`            INT           NOT NULL DEFAULT 0,
    `created_by`         VARCHAR(64)   NULL,
    `created_at`         TIMESTAMP     NULL,
    `updated_by`         VARCHAR(64)   NULL,
    `updated_at`         TIMESTAMP     NULL,
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_im_conversation_tenant_type` ON `im_conversation` (`tenant_id`, `conversation_type`);
CREATE INDEX `idx_im_conversation_status`      ON `im_conversation` (`tenant_id`, `status`);

CREATE TABLE `im_conversation_member` (
    `id`                   BIGINT        NOT NULL,
    `tenant_id`            BIGINT        NOT NULL DEFAULT 0,
    `conversation_id`      BIGINT        NOT NULL,
    `user_id`              BIGINT        NOT NULL,
    `member_role`          VARCHAR(20)   NOT NULL DEFAULT 'MEMBER',
    `last_read_message_id` BIGINT        NULL,
    `status`               VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    `version`              INT           NOT NULL DEFAULT 0,
    `deleted`              INT           NOT NULL DEFAULT 0,
    `created_at`           TIMESTAMP     NULL,
    `updated_at`           TIMESTAMP     NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uk_im_member_conv_user` UNIQUE (`tenant_id`, `conversation_id`, `user_id`)
);

CREATE INDEX `idx_im_member_tenant_conv` ON `im_conversation_member` (`tenant_id`, `conversation_id`);
CREATE INDEX `idx_im_member_tenant_user` ON `im_conversation_member` (`tenant_id`, `user_id`);

CREATE TABLE `im_message` (
    `id`              BIGINT        NOT NULL,
    `tenant_id`       BIGINT        NOT NULL DEFAULT 0,
    `conversation_id` BIGINT        NOT NULL,
    `sender_id`       BIGINT        NOT NULL,
    `content`         TEXT          NULL,
    `msg_type`        VARCHAR(20)   NOT NULL DEFAULT 'TEXT',
    `status`          VARCHAR(20)   NOT NULL DEFAULT 'SENT',
    `reply_to_id`     BIGINT        NULL,
    `version`         INT           NOT NULL DEFAULT 0,
    `deleted`         INT           NOT NULL DEFAULT 0,
    `created_by`      VARCHAR(64)   NULL,
    `created_at`      TIMESTAMP     NULL,
    `updated_by`      VARCHAR(64)   NULL,
    `updated_at`      TIMESTAMP     NULL,
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_im_message_tenant_conv` ON `im_message` (`tenant_id`, `conversation_id`);
CREATE INDEX `idx_im_message_created`     ON `im_message` (`tenant_id`, `conversation_id`, `created_at`);

-- ---------------- 菜单种子 ----------------

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9401, 0, 0, 'im', '站内消息', 'CATALOG', NULL, NULL, NULL, 'ChatDotRound', 1, 0, 90, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9402, 0, 9401, 'im:conversation', '会话管理', 'MENU', '/im/conversation', 'im/ConversationView', 'im:conversation:manage', 'ChatSquare', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9403, 0, 9401, 'im:message', '消息中心', 'MENU', '/im/message', 'im/MessageView', 'im:message:view', 'Message', 1, 0, 20, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9402;
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9403;

-- ############################################################
-- 来源: V76__approval_portal.sql
-- ############################################################

-- V76 审批中心门户（ROADMAP R7）：wf_task_ext 补 activity_name 快照列 + 审批中心菜单种子。
-- 设计要点：
--   1) activity_name 为任务创建期从工作流引擎捕获的活动（节点）名称快照，
--      待办/已办列表直接读取，无需回查引擎即可展示可读节点名；
--   2) ALTER 风格沿用 V69（workflow_delegate），标识符保留 snake_case 小写（H2 元数据库名小写、
--      生产 MySQL 大小写敏感，本仓既有迁移均以小写书写，保持一致）；
--   3) 审批中心为前端聚合页（待办/已办/我发起/可发起 + 统计），统一菜单码
--      approval:portal:view，由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动登记并授予 SUPER_ADMIN；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

ALTER TABLE wf_task_ext
    ADD COLUMN activity_name VARCHAR(255) NULL COMMENT '活动（节点）名称快照：任务创建期从引擎捕获，已办列表无需回查引擎即可展示可读节点名';

-- ---------------- 菜单种子 ----------------

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9501, 0, 0, 'approval', '审批中心', 'CATALOG', NULL, NULL, NULL, 'Tickets', 1, 0, 95, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9502, 0, 9501, 'approval:portal', '审批工作台', 'MENU', '/approval/portal', 'approval/PortalView', 'approval:portal:view', 'Tickets', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9502;

-- ############################################################
-- 来源: V77__ai_scaffold.sql
-- ############################################################

-- V77 AI 辅助开发（ROADMAP R3）：新增 scaffold-ai 模块 + AI 脚手架菜单种子。
-- 设计要点：
--   1) 标识符全部显式加引号保留 snake_case 小写（对齐 V74/V76 约定）；
--   2) AI 脚手架为只读生成入口，统一权限码 ai:scaffold:generate，
--      由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize 自动登记并授予 SUPER_ADMIN；
--   3) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9601, 0, 0, 'ai', 'AI 辅助开发', 'CATALOG', NULL, NULL, NULL, 'Cpu', 1, 0, 96, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9602, 0, 9601, 'ai:scaffold', '代码生成助手', 'MENU', '/ai/scaffold', 'ai/ScaffoldView', 'ai:scaffold:generate', 'MagicStick', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9602;

-- ############################################################
-- 来源: V78__dashboard_template.sql
-- ############################################################

-- V78 数据看板模板库（ROADMAP R8）：看板模板 CRUD + 布局配置（layout_json）+ 发布态流转。
-- 设计要点：
--   1) 标识符全部显式加引号保留 snake_case 小写（H2 元数据库名小写、生产 MySQL 大小写敏感，未引号会坏）；
--   2) 索引用独立 CREATE INDEX 语句（内联 KEY `x`(...) 会被 H2 误解析为 domain）；
--   3) tenant_id 承载租户隔离，enforce 关闭时恒为 0；
--   4) status 用 VARCHAR(20) 承载 DRAFT/PUBLISHED/OFFLINE，由应用层校验；
--   5) layout_json 用 TEXT 承载看板布局（栅格 / 卡片 / 图表配置），由前端以 JSON 字符串提交，
--      后端经 Jackson 校验合法性，SnakeRecordMapper 将 TEXT 直接映射为 String；
--   6) 菜单种子：挂载顶层「数据看板」目录（id=9701），下挂「看板模板管理」（9702），
--      permission_code 作为菜单可见性判定码，由 ApiResourceAutoSyncRunner 扫描 @PreAuthorize
--      自动落库并授予 SUPER_ADMIN，无需手工登记；
--   7) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

CREATE TABLE `dashboard_template` (
    `id`             BIGINT        NOT NULL,
    `tenant_id`      BIGINT        NOT NULL DEFAULT 0,
    `template_code`  VARCHAR(64)   NOT NULL,
    `template_name`  VARCHAR(128)  NOT NULL,
    `description`    VARCHAR(512)  NULL,
    `layout_json`    TEXT          NULL,
    `status`         VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    `view_count`     BIGINT        NOT NULL DEFAULT 0,
    `version`        INT           NOT NULL DEFAULT 0,
    `deleted`        INT           NOT NULL DEFAULT 0,
    `created_by`     VARCHAR(64)   NULL,
    `created_at`     TIMESTAMP     NULL,
    `updated_by`     VARCHAR(64)   NULL,
    `updated_at`     TIMESTAMP     NULL,
    PRIMARY KEY (`id`)
);

CREATE INDEX `idx_dashboard_template_tenant_code` ON `dashboard_template` (`tenant_id`, `template_code`);
CREATE INDEX `idx_dashboard_template_status`       ON `dashboard_template` (`tenant_id`, `status`);
CREATE INDEX `idx_dashboard_template_name`         ON `dashboard_template` (`tenant_id`, `template_name`);

-- ---------------- 菜单种子 ----------------

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9701, 0, 0, 'dashboard', '数据看板', 'CATALOG', NULL, NULL, NULL, 'Dashboard', 1, 0, 70, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9702, 0, 9701, 'dashboard:template', '看板模板管理', 'MENU', '/dashboard/template', 'dashboard/DashboardTemplateView', 'dashboard:template:manage', 'Grid', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9702;

-- ############################################################
-- 来源: V79__micro_frontend.sql
-- ############################################################

-- V79 微前端宿主（ROADMAP R12）：子应用挂载菜单种子。
-- 设计要点：
--   1) R12 采用离线安全的自研主壳方案（Vite import.meta.glob 懒加载 micro-apps/*/index.js 的
--      mount/unmount 契约），不引入 @originjs/vite-plugin-federation 外部依赖；
--   2) 宿主为纯前端页面（无新后端端点），permission_code 复用既有已登记的
--      system:monitor:read（与「系统监控」「健康探针」同权，参照 V42 health 菜单先例），
--      保证 SUPER_ADMIN 及持权角色可见；
--   3) 子应用清单由前端 src/micro-apps/*/index.js 目录结构驱动，新增子应用无需后端变更；
--   4) 幂等：INSERT IGNORE + SELECT 绑定 SUPER_ADMIN（role_id=1，tenant_id=0），可重复执行。

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9801, 0, 0, 'micro', '微前端', 'CATALOG', NULL, NULL, NULL, 'MagicStick', 1, 0, 92, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (9802, 0, 9801, 'micro:host', '微前端宿主', 'MENU', '/micro/host', 'micro/MicroFrontendHost', 'system:monitor:read', 'Menu', 1, 0, 10, 'ACTIVE', NOW(3), NOW(3));

INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id) SELECT 0, 1, 9802;

-- ############################################################
-- 来源: V80__chaos_config_center_menu.sql
-- ############################################################

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

-- ############################################################
-- 来源: V81__operation_log_query_index.sql
-- ############################################################

-- V81 审计日志查询索引补齐（E2E F1/F2 性能修复的数据层部分）。
-- 设计要点：
--   1) 现状：sys_operation_log 仅有 idx_module_time(tenant_id, module_code, occurred_at)、
--      idx_operator_time(tenant_id, operator_id, occurred_at)、idx_trace_id(trace_id)
--      三个二级索引，均以 tenant_id 打头。而「操作日志」页与工作台「最近动态」的查询条件
--      不带 tenant_id 前缀（单租户部署下 tenant_id 恒为 0，条件里没有该列），
--      导致 idx_operator_time 只能退化为全索引扫描（实测 127 万条 index 条目）
--      再叠加 ORDER BY occurred_at, id 的文件排序，单次请求 11.9~16.4s；
--   2) 本次补两个「无 tenant_id 前缀」的覆盖索引：
--        - idx_operator_occurred (operator_id, occurred_at, id)
--             服务 WHERE operator_id = ? ORDER BY occurred_at DESC, id DESC LIMIT n
--             （工作台「最近动态」/ 按操作人筛审计）；
--        - idx_occurred_id (occurred_at, id)
--             服务无筛选时的 ORDER BY occurred_at DESC, id DESC LIMIT n（列表首页）
--             与 occurred_at 区间筛选（rows 直接由索引给出，免回表排序）；
--   3) 排序键包含 id 作为 tie-breaker，索引列顺序与之严格一致，保证倒序扫描即可满足
--      ORDER BY，不再触发 filesort；
--   4) 索引名全局唯一（已全量检索 db/ 目录确认无重名）；标识符统一用反引号包裹并保留
--      snake_case 小写（与 V74 等既有迁移一致）。注意：MySQL 未开启 ANSI_QUOTES 时双引号是
--      字符串字面量而非标识符，CREATE INDEX "x" ... 会直接语法报错（本文件首版即踩此坑）；
--   5) CREATE INDEX 不支持 IF NOT EXISTS，Flyway 保证本文件只执行一次。

CREATE INDEX `idx_operator_occurred` ON `sys_operation_log` (`operator_id`, `occurred_at`, `id`);

CREATE INDEX `idx_occurred_id` ON `sys_operation_log` (`occurred_at`, `id`);

-- ############################################################
-- 来源: V82__align_menu_permission_codes.sql
-- ############################################################

-- V82 菜单权限码对齐（若依规范 P0-1）：修正 2 条「菜单授予」与「接口要求」不一致的存量数据。
--
-- 背景：V32 基线种子为「部门管理」写入 system:department:read、为「接口资源管理」写入
--   system:api-resource:read，但后端实际 @PreAuthorize 分别为
--   system:org:read（OrgController L41）与 system:api:read（ApiResourceController L37）。
--   SUPER_ADMIN 全量放行掩盖了问题；普通角色按菜单授权后进入页面即 403。
--   前端无权限码硬编码（已全量核实），改数据不影响可见性逻辑。
--
-- 配套：V32 基线种子已同步改为正确值，全新初始化直接得到正确数据；
--   本脚本仅用于修正「V32 之前已经初始化」的既有库（INSERT IGNORE 不会覆盖旧行）。
--
-- 幂等：WHERE 以主键 id 限定且仅当目标值不同才更新，可重复执行；
--   刻意不写旧权限码字面量，避免被 check-permission.py 误采为菜单种子值。
-- 影响：2 行数据，无 DDL、无接口变更、无业务列变更。

UPDATE sys_menu
   SET permission_code = 'system:org:read', updated_at = NOW(3)
 WHERE id = 1013
   AND (permission_code IS NULL OR permission_code <> 'system:org:read');

UPDATE sys_menu
   SET permission_code = 'system:api:read', updated_at = NOW(3)
 WHERE id = 1014
   AND (permission_code IS NULL OR permission_code <> 'system:api:read');

-- ############################################################
-- 来源: V83__sys_menu_created_by.sql
-- ############################################################

-- V83 sys_menu 审计创建人补齐（若依规范对齐 P0-2）
--
-- 背景：V32 基线中 sys_user / sys_org / sys_role 等主数据表均带 created_by / updated_by
--   审计列，唯独 sys_menu 缺失 created_by，导致「谁新建了这条菜单」无法追溯。
--   本版本仅补 created_by（不补 updated_by），保持与报告 §3 P0-2 的改动范围一致。
--
-- 版本链约束：db/migration/README.md 明令「版本文件字节不可变、结构性变更一律前向追加」，
--   故 V32 基表定义保持原样，新库由 V32 建表 → 本文件补列，结果与直接改基表等价。
--
-- 幂等：沿用 V32 L518–L535 的「列缺失才补」模式（information_schema 守卫 + 动态 DDL）。
--   MySQL 无事务性 DDL，迁移中途失败会留下已生效的结构；重复执行命中 'DO 0' 直接无操作，
--   不会撞 1060 Duplicate column。
-- 兼容：列可空，存量行一律为 NULL，不回填、不造数据；不影响任何既有查询与写入路径。
-- 影响：1 张表 1 列；无接口变更、无前端变更。

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_menu`
             ADD COLUMN `created_by` BIGINT UNSIGNED NULL COMMENT ''创建人用户ID'' AFTER `status`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_menu' AND COLUMN_NAME = 'created_by'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ############################################################
-- 来源: V84__rename_alert_rule.sql
-- ############################################################

-- V84 告警规则表补模块前缀（若依规范对齐 P2-2）：alert_rule -> sys_alert_rule
--
-- 背景：V70 建立的 alert_rule 是全仓唯一缺少 sys_/wf_/cms_/im_ 等业务前缀的主数据表，
--   与「表由模块独占写入、按域加前缀」的既有约定不一致。本版本只改表名，不改任何列、
--   索引、数据、接口与前端行为。
--
-- 版本链约束：db/migration/README.md 明令「版本文件字节不可变、结构性变更一律前向追加」，
--   故 V70 的 CREATE TABLE alert_rule 保持原样；新库由 V70 建表 → 本文件重命名，
--   最终落态与「V70 直接建 sys_alert_rule」等价，且存量库可平滑升级。
--
-- 幂等：information_schema 双守卫 —— 仅当 alert_rule 存在且 sys_alert_rule 不存在时才 RENAME；
--   重复执行 / 已是新表名的库命中 'DO 0' 无操作，不会撞 1050/1060 类错误。
--   MySQL 的 RENAME TABLE 是原子 DDL，索引（idx_metric_enabled）、数据、自增状态均随表保留。
--
-- 【部署顺序·必读】这是结构级不兼容变更，Java 侧 SqlTables.ALERT_RULE 已同步指向 sys_alert_rule：
--   1) 先备份：mysqldump --single-transaction <db> alert_rule > alert_rule_backup.sql
--   2) 停写窗口内执行本迁移（RENAME 需短暂 MDL 锁，期间告警规则读写会阻塞）
--   3) 确认 SELECT COUNT(*) FROM sys_alert_rule 可用后，再发布含新常量的应用版本
--   反向发布（先上应用、后跑迁移）会因表不存在导致告警规则模块整体不可用。
--
-- 【回滚】如需回到旧表名，先下线新版应用，再执行（同样需要停写窗口）：
--   RENAME TABLE `sys_alert_rule` TO `alert_rule`;
--   并同步把 SqlTables.ALERT_RULE 的表名字符串改回 alert_rule 后重新发布。

SET @ddl = (
    SELECT IF(
        (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alert_rule') = 1
        AND (SELECT COUNT(*) FROM information_schema.TABLES
          WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_alert_rule') = 0,
        'RENAME TABLE `alert_rule` TO `sys_alert_rule`',
        'DO 0')
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;


-- ============================================================
-- 初始化基线标记（用于 scripts/init/verify_install.py 校验）
-- ============================================================
CREATE TABLE IF NOT EXISTS `schema_baseline` (
  `baseline_version` VARCHAR(32) NOT NULL,
  `source_count` INT NOT NULL DEFAULT 0,
  `applied_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`baseline_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 清掉旧基线，确保表中只保留当前已初始化的版本
DELETE FROM `schema_baseline`;

INSERT INTO `schema_baseline` (`baseline_version`, `source_count`) VALUES ('V84', 52);
