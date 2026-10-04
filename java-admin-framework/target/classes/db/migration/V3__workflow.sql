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
