-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V3__workflow.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS wf_definition_ext (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, process_key VARCHAR(128) NOT NULL, process_name VARCHAR(200) NOT NULL, version INT NOT NULL, deployment_id VARCHAR(64) NOT NULL, process_definition_id VARCHAR(128) NOT NULL, form_schema VARCHAR(4000) NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', published_by BIGINT NULL, published_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX wf_definition_ext_uk_process_version ON wf_definition_ext (tenant_id, process_key, version);

CREATE UNIQUE INDEX wf_definition_ext_uk_engine_definition ON wf_definition_ext (process_definition_id);

CREATE TABLE IF NOT EXISTS wf_node_config (id BIGINT NOT NULL, definition_ext_id BIGINT NOT NULL, activity_id VARCHAR(128) NOT NULL, node_name VARCHAR(200) NOT NULL, approval_mode VARCHAR(20) NOT NULL DEFAULT 'ANY', approval_ratio DECIMAL(5,2) NULL, assignee_type VARCHAR(32) NOT NULL, assignee_expression VARCHAR(1000) NOT NULL, reject_policy VARCHAR(32) NOT NULL DEFAULT 'PREVIOUS', reject_target_activity_id VARCHAR(128) NULL, allow_transfer TINYINT NOT NULL DEFAULT 1, allow_delegate TINYINT NOT NULL DEFAULT 0, timeout_minutes INT NULL, config_json VARCHAR(4000) NULL, PRIMARY KEY (id));

CREATE UNIQUE INDEX wf_node_config_uk_definition_activity ON wf_node_config (definition_ext_id, activity_id);

CREATE TABLE IF NOT EXISTS wf_instance_ext (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, process_instance_id VARCHAR(64) NOT NULL, process_definition_id VARCHAR(128) NOT NULL, business_type VARCHAR(64) NOT NULL, business_id VARCHAR(128) NOT NULL, title VARCHAR(300) NOT NULL, starter_user_id BIGINT NOT NULL, starter_org_id BIGINT NULL, current_activity_id VARCHAR(128) NULL, status VARCHAR(20) NOT NULL, started_at DATETIME(3) NOT NULL, finished_at DATETIME(3) NULL, version INT NOT NULL DEFAULT 0, PRIMARY KEY (id));

CREATE UNIQUE INDEX wf_instance_ext_uk_process_instance ON wf_instance_ext (process_instance_id);

CREATE UNIQUE INDEX wf_instance_ext_uk_business_instance ON wf_instance_ext (tenant_id, business_type, business_id);

CREATE INDEX wf_instance_ext_idx_starter_status ON wf_instance_ext (tenant_id, starter_user_id, status);

CREATE INDEX wf_instance_ext_idx_status_started ON wf_instance_ext (tenant_id, status, started_at);

CREATE TABLE IF NOT EXISTS wf_task_ext (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, task_id VARCHAR(64) NOT NULL, process_instance_id VARCHAR(64) NOT NULL, activity_id VARCHAR(128) NOT NULL, assignee_user_id BIGINT NULL, original_assignee_user_id BIGINT NULL, status VARCHAR(20) NOT NULL, approval_mode VARCHAR(20) NOT NULL, due_at DATETIME(3) NULL, claimed_at DATETIME(3) NULL, completed_at DATETIME(3) NULL, version INT NOT NULL DEFAULT 0, PRIMARY KEY (id));

CREATE UNIQUE INDEX wf_task_ext_uk_task_id ON wf_task_ext (task_id);

CREATE INDEX wf_task_ext_idx_assignee_status ON wf_task_ext (tenant_id, assignee_user_id, status);

CREATE INDEX wf_task_ext_idx_instance_activity ON wf_task_ext (process_instance_id, activity_id);

CREATE TABLE IF NOT EXISTS wf_approval_record (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, operation_id VARCHAR(64) NOT NULL, process_instance_id VARCHAR(64) NOT NULL, task_id VARCHAR(64) NULL, activity_id VARCHAR(128) NULL, action VARCHAR(32) NOT NULL, operator_user_id BIGINT NOT NULL, from_user_id BIGINT NULL, to_user_id BIGINT NULL, opinion VARCHAR(2000) NULL, attachment_refs VARCHAR(4000) NULL, snapshot VARCHAR(4000) NULL, trace_id VARCHAR(64) NULL, occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX wf_approval_record_uk_operation_id ON wf_approval_record (operation_id);

CREATE INDEX wf_approval_record_idx_instance_time ON wf_approval_record (tenant_id, process_instance_id, occurred_at);

CREATE INDEX wf_approval_record_idx_task_time ON wf_approval_record (task_id, occurred_at);

