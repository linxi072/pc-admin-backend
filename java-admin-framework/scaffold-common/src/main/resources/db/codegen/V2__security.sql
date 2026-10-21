-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V2__security.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS sys_refresh_token (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, user_id BIGINT NOT NULL, client_id VARCHAR(64) NOT NULL, session_id VARCHAR(64) NOT NULL, family_id VARCHAR(64) NOT NULL, token_hash CHAR(64) NOT NULL, device_id VARCHAR(128) NULL, user_agent VARCHAR(500) NULL, ip_address VARCHAR(64) NULL, issued_at DATETIME(3) NOT NULL, expires_at DATETIME(3) NOT NULL, last_used_at DATETIME(3) NULL, revoked_at DATETIME(3) NULL, revoke_reason VARCHAR(64) NULL, replaced_by_id BIGINT NULL, reuse_detected TINYINT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0, PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_refresh_token_uk_token_hash ON sys_refresh_token (token_hash);

CREATE INDEX sys_refresh_token_idx_user_expiry ON sys_refresh_token (tenant_id, user_id, expires_at);

CREATE INDEX sys_refresh_token_idx_family ON sys_refresh_token (tenant_id, family_id);

CREATE TABLE IF NOT EXISTS sys_password_history (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, user_id BIGINT NOT NULL, password_hash VARCHAR(255) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE INDEX sys_password_history_idx_user_time ON sys_password_history (tenant_id, user_id, created_at);

CREATE TABLE IF NOT EXISTS sys_login_log (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, user_id BIGINT NULL, username VARCHAR(64) NULL, login_type VARCHAR(32) NOT NULL, result VARCHAR(20) NOT NULL, failure_code VARCHAR(64) NULL, ip_address VARCHAR(64) NULL, user_agent VARCHAR(500) NULL, trace_id VARCHAR(64) NULL, occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE INDEX sys_login_log_idx_user_time ON sys_login_log (tenant_id, user_id, occurred_at);

CREATE INDEX sys_login_log_idx_ip_time ON sys_login_log (ip_address, occurred_at);

CREATE INDEX sys_login_log_idx_result_time ON sys_login_log (result, occurred_at);

CREATE TABLE IF NOT EXISTS sys_operation_log (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, module_code VARCHAR(64) NOT NULL, operation_type VARCHAR(64) NOT NULL, operation_name VARCHAR(128) NOT NULL, operator_id BIGINT NULL, operator_name VARCHAR(100) NULL, request_method VARCHAR(16) NULL, request_path VARCHAR(500) NULL, request_summary VARCHAR(4000) NULL, result_summary VARCHAR(4000) NULL, result_code VARCHAR(64) NULL, duration_ms BIGINT NOT NULL DEFAULT 0, ip_address VARCHAR(64) NULL, trace_id VARCHAR(64) NULL, success TINYINT NOT NULL, occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE INDEX sys_operation_log_idx_module_time ON sys_operation_log (tenant_id, module_code, occurred_at);

CREATE INDEX sys_operation_log_idx_operator_time ON sys_operation_log (tenant_id, operator_id, occurred_at);

CREATE INDEX sys_operation_log_idx_trace_id ON sys_operation_log (trace_id);

