-- =====================================================================================
--  schema_ddl.sql —— 全量结构定义（DDL）分类汇总
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


-- [V1__baseline.sql L2-30]
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

-- [V1__baseline.sql L32-53]
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

-- [V1__baseline.sql L55-65]
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

-- [V1__baseline.sql L67-84]
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

-- [V1__baseline.sql L86-98]
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

-- [V1__baseline.sql L100-123]
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

-- [V1__baseline.sql L125-141]
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

-- [V1__baseline.sql L143-150]
CREATE TABLE IF NOT EXISTS sys_role_menu (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    menu_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, role_id, menu_id),
    KEY idx_menu_role (tenant_id, menu_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- [V1__baseline.sql L152-159]
CREATE TABLE IF NOT EXISTS sys_role_api (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    role_id BIGINT UNSIGNED NOT NULL,
    api_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (tenant_id, role_id, api_id),
    KEY idx_api_role (tenant_id, api_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- [V1__baseline.sql L161-172]
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

-- [V1__baseline.sql L174-180]
CREATE TABLE IF NOT EXISTS sys_role_data_scope_org (
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rule_id BIGINT UNSIGNED NOT NULL,
    org_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (tenant_id, rule_id, org_id),
    KEY idx_org_rule (tenant_id, org_id, rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- =====================================================================================
-- 来源：V2__security.sql
-- =====================================================================================


-- [V2__security.sql L2-25]
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

-- [V2__security.sql L27-35]
CREATE TABLE IF NOT EXISTS sys_password_history (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT UNSIGNED NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_time (tenant_id, user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- [V2__security.sql L37-53]
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

-- [V2__security.sql L55-77]
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
-- 来源：V3__workflow.sql
-- =====================================================================================


-- [V3__workflow.sql L2-18]
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

-- [V3__workflow.sql L20-37]
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

-- [V3__workflow.sql L39-59]
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

-- [V3__workflow.sql L61-79]
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

-- [V3__workflow.sql L81-101]
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
-- 来源：V5__system_modules.sql
-- =====================================================================================


-- [V5__system_modules.sql L4-21]
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

-- [V5__system_modules.sql L23-41]
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

-- [V5__system_modules.sql L43-61]
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    config_key VARCHAR(64) NOT NULL,
    config_name VARCHAR(100) NOT NULL,
    config_value VARCHAR(500) NULL,
    default_value VARCHAR(500) NULL COMMENT '参数默认值（工厂默认值，区别于当前生效值 config_value）',
    config_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    remark VARCHAR(255) NULL,
    editable TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否可编辑：1=运行时可改，0=系统内置不可改',
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


-- =====================================================================================
-- 来源：V6__user_single_role_org.sql
-- =====================================================================================


-- [V6__user_single_role_org.sql L18-26]
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
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V6__user_single_role_org.sql L28-36]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `org_id` BIGINT UNSIGNED NULL COMMENT ''单一部门ID(收敛后不再允许多部门)'' AFTER `role_id`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V6__user_single_role_org.sql L39-46]
-- 索引同理，缺失才补（重复添加会报 1061 Duplicate key name）
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user` ADD KEY `idx_tenant_role` (`tenant_id`, `role_id`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_role'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V6__user_single_role_org.sql L48-55]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user` ADD KEY `idx_tenant_org` (`tenant_id`, `org_id`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_org'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V6__user_single_role_org.sql L117]
-- 5) 清理多对多关联表（收敛后不再有查询/写入方引用）。
DROP TABLE IF EXISTS sys_user_role;

-- [V6__user_single_role_org.sql L118]
DROP TABLE IF EXISTS sys_user_org;


-- =====================================================================================
-- 来源：V7__announcement.sql
-- =====================================================================================


-- [V7__announcement.sql L9-37]
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
-- 来源：V8__message.sql
-- =====================================================================================


-- [V8__message.sql L7-31]
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

-- [V8__message.sql L33-47]
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
-- 来源：V9__monitor_sample.sql
-- =====================================================================================


-- [V9__monitor_sample.sql L6-29]
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
-- 来源：V10__user_profile.sql
-- =====================================================================================


-- [V10__user_profile.sql L11-19]
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
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V10__user_profile.sql L22-30]
-- 2) 通知偏好：三个开关独立列，便于「只开站内信、关邮件」这类组合
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_site_message` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''站内信通知开关'' AFTER `avatar_url`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_site_message'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V10__user_profile.sql L32-40]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_email` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''邮件通知开关'' AFTER `notify_site_message`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_email'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V10__user_profile.sql L42-50]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `notify_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT ''短信通知开关'' AFTER `notify_email`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'notify_mobile'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V10__user_profile.sql L53-61]
-- 3) 隐私偏好：登录日志展示、脱敏手机号、允许被搜索
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `show_login_log` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''是否展示我的登录记录'' AFTER `notify_mobile`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'show_login_log'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V10__user_profile.sql L63-71]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `mask_mobile` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''对外展示时脱敏手机号'' AFTER `show_login_log`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'mask_mobile'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V10__user_profile.sql L73-81]
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_user`
             ADD COLUMN `discoverable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''允许被其他用户搜索到'' AFTER `mask_mobile`',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'discoverable'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V10__user_profile.sql L84-92]
-- 4) 登录设备索引：支撑「我的登录设备」按用户聚合查询
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_refresh_token`
             ADD KEY `idx_user_revoked` (`user_id`, `revoked_at`)',
        'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_refresh_token' AND INDEX_NAME = 'idx_user_revoked'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================================
-- 来源：V13__workflow_design.sql
-- =====================================================================================


-- [V13__workflow_design.sql L3-23]
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
-- 来源：V15__user_role_org_many_to_many.sql
-- =====================================================================================


-- [V15__user_role_org_many_to_many.sql L9-27]
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
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V15__user_role_org_many_to_many.sql L30-48]
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
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V15__user_role_org_many_to_many.sql L74-79]
-- 4) 收敛列下线：删除 sys_user 的 role_id / org_id / primary_org_id（含索引）。
--    删除前关联数据已写入 sys_user_role / sys_user_org，主角色/主部门改由关联表 is_primary 推导。
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP INDEX `idx_tenant_role`', 'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_role'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V15__user_role_org_many_to_many.sql L81-86]
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP INDEX `idx_tenant_org`', 'DO 0')
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'idx_tenant_org'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V15__user_role_org_many_to_many.sql L88-93]
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `role_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'role_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V15__user_role_org_many_to_many.sql L95-100]
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `org_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'org_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- [V15__user_role_org_many_to_many.sql L102-107]
SET @ddl = (
    SELECT IF(COUNT(*) > 0, 'ALTER TABLE `sys_user` DROP COLUMN `primary_org_id`', 'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'primary_org_id'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- =====================================================================================
-- 来源：V16__idempotency_record.sql
-- =====================================================================================


-- [V16__idempotency_record.sql L11-28]
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
-- 来源：V21__job_management.sql
-- =====================================================================================


-- [V21__job_management.sql L17-49]
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

-- [V21__job_management.sql L54-70]
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


-- =====================================================================================
-- 来源：V23__workflow_design_deleted.sql
-- =====================================================================================


-- [V23__workflow_design_deleted.sql L5-7]
-- V23 工作流设计表补逻辑删除列：修复「删除即物理消失、前端陈旧行操作报不存在」的数据一致性缺陷。
-- 背景：wf_workflow_design 原无 deleted 列，JooqWriters.delete 的逻辑删除退化为物理删除，
-- 删除后行不可追溯，且同一 processKey 可被无痕重建（seeder 反复播种产生新 id）。
-- 本迁移补列后，服务层删除走 deleted=1 逻辑删除；既有行默认 0（可见），不影响存量数据。
ALTER TABLE wf_workflow_design
    ADD COLUMN deleted TINYINT UNSIGNED NOT NULL DEFAULT 0 AFTER version,
    ADD KEY idx_tenant_status_deleted (tenant_id, status, deleted);


-- =====================================================================================
-- 来源：V27__online_user_management.sql
-- =====================================================================================


-- [V27__online_user_management.sql L16-37]
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

-- [V27__online_user_management.sql L39-65]
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


-- =====================================================================================
-- 来源：permission-schema.sql
-- =====================================================================================


-- [permission-schema.sql L14-27]
-- ----------------------------
-- 1. 用户表 sys_user
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username     VARCHAR(64)  NOT NULL COMMENT '登录账号',
  password     VARCHAR(128) NOT NULL DEFAULT '' COMMENT '密码（加密存储）',
  display_name VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '显示名称/昵称',
  email        VARCHAR(128) DEFAULT '' COMMENT '邮箱',
  mobile       VARCHAR(32)  DEFAULT '' COMMENT '手机号',
  avatar_url   VARCHAR(255) DEFAULT '' COMMENT '头像URL',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- [permission-schema.sql L32-44]
-- ----------------------------
-- 2. 角色表 sys_role
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role (
  id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  role_code    VARCHAR(64)  NOT NULL COMMENT '角色编码（程序标识，如 SUPER_ADMIN）',
  role_name    VARCHAR(64)  NOT NULL COMMENT '角色名称',
  role_type    VARCHAR(32)  NOT NULL DEFAULT 'BUSINESS' COMMENT '角色类型：SYSTEM=内置 BUSINESS=业务',
  description  VARCHAR(255) DEFAULT '' COMMENT '角色描述',
  status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  sort_no      INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- [permission-schema.sql L49-63]
-- ----------------------------
-- 3. 部门表 sys_department（支持树形层级）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_department (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  dept_code  VARCHAR(64)  NOT NULL COMMENT '部门编码',
  dept_name  VARCHAR(64)  NOT NULL COMMENT '部门名称',
  parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父部门ID（0=顶级）',
  ancestors  VARCHAR(512) NOT NULL DEFAULT '' COMMENT '祖级ID路径，逗号分隔',
  leader     VARCHAR(64)  DEFAULT '' COMMENT '负责人',
  sort_no    INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_dept_code (dept_code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='部门表';

-- [permission-schema.sql L68-86]
-- ----------------------------
-- 4. 菜单表 sys_menu（支持树形层级）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_menu (
  id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  parent_id  BIGINT       NOT NULL DEFAULT 0 COMMENT '父菜单ID（0=顶级）',
  menu_name  VARCHAR(64)  NOT NULL COMMENT '菜单名称',
  menu_code  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '菜单编码（唯一标识）',
  menu_type  TINYINT      NOT NULL DEFAULT 1 COMMENT '类型：1=目录 2=菜单 3=按钮',
  path       VARCHAR(128) DEFAULT '' COMMENT '路由路径',
  component  VARCHAR(128) DEFAULT '' COMMENT '前端组件路径',
  icon       VARCHAR(64)  DEFAULT '' COMMENT '图标',
  permission VARCHAR(64)  DEFAULT '' COMMENT '权限标识（后端接口/auth码）',
  sort_no    INT          NOT NULL DEFAULT 0 COMMENT '排序号',
  visible    TINYINT      NOT NULL DEFAULT 1 COMMENT '是否显示：1=显示 0=隐藏',
  status     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_menu_code (menu_code),
  KEY idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单表';

-- [permission-schema.sql L91-101]
-- ----------------------------
-- 5. 用户-角色关联表（多对多）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_role (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT   NOT NULL COMMENT '用户ID',
  role_id    BIGINT   NOT NULL COMMENT '角色ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role (role_id),
  CONSTRAINT fk_ur_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_ur_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色关联表';

-- [permission-schema.sql L106-117]
-- ----------------------------
-- 6. 用户-部门关联表（多对多，可标记主部门）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_user_department (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id    BIGINT   NOT NULL COMMENT '用户ID',
  dept_id    BIGINT   NOT NULL COMMENT '部门ID',
  is_primary TINYINT  NOT NULL DEFAULT 0 COMMENT '是否主部门：1=是 0=否',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_dept (user_id, dept_id),
  KEY idx_dept (dept_id),
  CONSTRAINT fk_ud_user FOREIGN KEY (user_id) REFERENCES sys_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_ud_dept FOREIGN KEY (dept_id) REFERENCES sys_department (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-部门关联表';

-- [permission-schema.sql L122-132]
-- ----------------------------
-- 7. 角色-菜单关联表（多对多）
-- ----------------------------
CREATE TABLE IF NOT EXISTS sys_role_menu (
  id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  role_id    BIGINT   NOT NULL COMMENT '角色ID',
  menu_id    BIGINT   NOT NULL COMMENT '菜单ID',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_menu (role_id, menu_id),
  KEY idx_menu (menu_id),
  CONSTRAINT fk_rm_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE CASCADE,
  CONSTRAINT fk_rm_menu FOREIGN KEY (menu_id) REFERENCES sys_menu (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单关联表';

