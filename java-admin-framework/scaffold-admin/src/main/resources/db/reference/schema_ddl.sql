-- ============================================================
-- 分类参考件（DDL / DML），由 scripts/build_reference_modular.py 从
-- db/migration/ 迁移链（V32~V59）自动派生，仅供阅读与追溯。
-- 权威可执行来源：db/migration/ 迁移链（V57 为 Java 类迁移）。
-- 不可当迁移执行；Flyway 只扫描 db/migration/。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
CREATE TABLE IF NOT EXISTS sys_role_menu (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    menu_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, role_id, menu_id),
    KEY idx_menu_role (tenant_id, menu_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- [from V32__consolidated_schema.sql]
CREATE TABLE IF NOT EXISTS sys_role_api (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    api_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, role_id, api_id),
    KEY idx_api_role (tenant_id, api_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
CREATE TABLE IF NOT EXISTS sys_role_data_scope_org (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rule_id BIGINT UNSIGNED NOT NULL,
    org_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (tenant_id, rule_id, org_id),
    KEY idx_org_rule (tenant_id, org_id, rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
CREATE TABLE IF NOT EXISTS sys_password_history (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_time (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `org_id` BIGINT UNSIGNED NULL COMMENT ''单一部门ID(收敛后不再允许多部门)'' AFTER `role_id`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
-- 索引同理，缺失才补（重复添加会报 1061 Duplicate key name）
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user` ADD KEY `idx_tenant_role` (`tenant_id`, `role_id`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_role'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user` ADD KEY `idx_tenant_org` (`tenant_id`, `org_id`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_org'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
-- 5) 清理多对多关联表（收敛后不再有查询/写入方引用）。
DROP TABLE IF EXISTS sys_user_role;

-- [from V32__consolidated_schema.sql]
DROP TABLE IF EXISTS sys_user_org;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
-- 2) 通知偏好：三个开关独立列，便于「只开站内信、关邮件」这类组合
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_site_message` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''站内信通知开关'' AFTER `avatar_url`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_site_message'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_email` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''邮件通知开关'' AFTER `notify_site_message`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_email'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''短信通知开关'' AFTER `notify_email`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_mobile'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
-- 3) 隐私偏好：登录日志展示、脱敏手机号、允许被搜索
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `show_login_log` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''是否展示我的登录记录'' AFTER `notify_mobile`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'show_login_log'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `mask_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''对外展示时脱敏手机号'' AFTER `show_login_log`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'mask_mobile'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `discoverable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''允许被其他用户搜索到'' AFTER `mask_mobile`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'discoverable'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
-- 4) 登录设备索引：支撑「我的登录设备」按用户聚合查询
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_refresh_token`
             ADD KEY `idx_user_revoked` (`user_id`, `revoked_at`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_refresh_token' AND INDEX_NAME = 'idx_user_revoked'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) > 0,
        'INSERT IGNORE INTO sys_user_org (tenant_id, user_id, org_id, is_primary, created_at)
         SELECT 0, id, org_id, 1, NOW(3) FROM sys_user WHERE org_id IS NOT NULL',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
-- 4) 收敛列下线：删除 sys_user 的 role_id / org_id / primary_org_id（含索引）。
--    删除前关联数据已写入 sys_user_role / sys_user_org，主角色/主部门改由关联表 is_primary 推导。
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP INDEX `idx_tenant_role`', 'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_role'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP INDEX `idx_tenant_org`', 'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_org'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `role_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `org_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `primary_org_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'primary_org_id'
);

-- [from V32__consolidated_schema.sql]
PREPARE stmt FROM @ddl;

-- [from V32__consolidated_schema.sql]
EXECUTE stmt;

-- [from V32__consolidated_schema.sql]
DEALLOCATE PREPARE stmt;

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V32__consolidated_schema.sql]
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

-- [from V33__file_management.sql]
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

-- [from V35__post_management.sql]
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

-- [from V35__post_management.sql]
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

-- [from V37__sms_record.sql]
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

-- [from V38__system_parameters.sql]
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

-- [from V38__system_parameters.sql]
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

-- [from V38__system_parameters.sql]
PREPARE stmt FROM @ddl;

-- [from V38__system_parameters.sql]
EXECUTE stmt;

-- [from V38__system_parameters.sql]
DEALLOCATE PREPARE stmt;

-- [from V38__system_parameters.sql]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_config` ADD COLUMN `editable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''是否可编辑：1=运行时可改，0=系统内置不可改''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config' AND COLUMN_NAME = 'editable'
);

-- [from V38__system_parameters.sql]
PREPARE stmt FROM @ddl;

-- [from V38__system_parameters.sql]
EXECUTE stmt;

-- [from V38__system_parameters.sql]
DEALLOCATE PREPARE stmt;

-- [from V40__announcement_read_receipt.sql]
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

-- [from V40__announcement_read_receipt.sql]
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

-- [from V40__announcement_read_receipt.sql]
PREPARE stmt FROM @ddl;

-- [from V40__announcement_read_receipt.sql]
EXECUTE stmt;

-- [from V40__announcement_read_receipt.sql]
DEALLOCATE PREPARE stmt;

-- [from V40__announcement_read_receipt.sql]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_announcement` ADD COLUMN `read_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''已读人数（markRead 成功时 +1）''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_announcement' AND COLUMN_NAME = 'read_count'
);

-- [from V40__announcement_read_receipt.sql]
PREPARE stmt FROM @ddl;

-- [from V40__announcement_read_receipt.sql]
EXECUTE stmt;

-- [from V40__announcement_read_receipt.sql]
DEALLOCATE PREPARE stmt;

-- [from V41__notification_template.sql]
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

-- [from V42__notification_record.sql]
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

-- [from V43__notification_preference.sql]
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

-- [from V44__notification_quiet_hours.sql]
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

-- [from V45__notification_digest.sql]
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

-- [from V46__mask_strategy_center.sql]
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

-- [from V47__form_pilot.sql]
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

-- [from V48__openapi_client.sql]
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

-- [from V50__file_dedup.sql]
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

-- [from V50__file_dedup.sql]
PREPARE stmt FROM @ddl;

-- [from V50__file_dedup.sql]
EXECUTE stmt;

-- [from V50__file_dedup.sql]
DEALLOCATE PREPARE stmt;

-- [from V50__file_dedup.sql]
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

-- [from V50__file_dedup.sql]
PREPARE stmt FROM @ddl;

-- [from V50__file_dedup.sql]
EXECUTE stmt;

-- [from V50__file_dedup.sql]
DEALLOCATE PREPARE stmt;

-- [from V51__tenant_management.sql]
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

-- [from V53__form_submission.sql]
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

-- [from V59__job_dependency.sql]
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

-- [from V59__job_dependency.sql]
SET @col = (SELECT COUNT(*) FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_job' AND COLUMN_NAME = 'depends_on');

-- [from V59__job_dependency.sql]
SET @sql = IF(@col = 0,
    'ALTER TABLE sys_job ADD COLUMN depends_on VARCHAR(1024) DEFAULT NULL COMMENT ''依赖任务ID列表(JSON数组)''',
    'SELECT 1');

-- [from V59__job_dependency.sql]
PREPARE stmt FROM @sql;

-- [from V59__job_dependency.sql]
EXECUTE stmt;

-- [from V59__job_dependency.sql]
DEALLOCATE PREPARE stmt;
