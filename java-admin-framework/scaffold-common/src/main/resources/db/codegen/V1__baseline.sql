-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V1__baseline.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS sys_user (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, username VARCHAR(64) NOT NULL, password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(100) NOT NULL, mobile VARCHAR(32) NULL, email VARCHAR(128) NULL, primary_org_id BIGINT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', failed_login_count INT NOT NULL DEFAULT 0, locked_until DATETIME(3) NULL, password_changed_at DATETIME(3) NULL, password_expired TINYINT NOT NULL DEFAULT 0, token_version INT NOT NULL DEFAULT 1, mfa_enabled TINYINT NOT NULL DEFAULT 0, last_login_at DATETIME(3) NULL, version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_user_uk_tenant_username ON sys_user (tenant_id, username);

CREATE UNIQUE INDEX sys_user_uk_tenant_mobile ON sys_user (tenant_id, mobile);

CREATE INDEX sys_user_idx_org_status ON sys_user (tenant_id, primary_org_id, status);

CREATE INDEX sys_user_idx_updated_at ON sys_user (updated_at);

CREATE TABLE IF NOT EXISTS sys_org (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, parent_id BIGINT NOT NULL DEFAULT 0, ancestors VARCHAR(1000) NOT NULL DEFAULT '0', org_code VARCHAR(64) NOT NULL, org_name VARCHAR(128) NOT NULL, org_type VARCHAR(32) NOT NULL DEFAULT 'DEPARTMENT', sort_no INT NOT NULL DEFAULT 0, leader_user_id BIGINT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_org_uk_tenant_org_code ON sys_org (tenant_id, org_code);

CREATE INDEX sys_org_idx_parent ON sys_org (tenant_id, parent_id, sort_no);

CREATE INDEX sys_org_idx_ancestors ON sys_org (tenant_id, ancestors);

CREATE TABLE IF NOT EXISTS sys_user_org (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, user_id BIGINT NOT NULL, org_id BIGINT NOT NULL, is_primary TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_user_org_uk_user_org ON sys_user_org (tenant_id, user_id, org_id);

CREATE INDEX sys_user_org_idx_org_user ON sys_user_org (tenant_id, org_id, user_id);

CREATE TABLE IF NOT EXISTS sys_role (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, role_code VARCHAR(64) NOT NULL, role_name VARCHAR(100) NOT NULL, role_type VARCHAR(20) NOT NULL DEFAULT 'BUSINESS', status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', sort_no INT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_role_uk_tenant_role_code ON sys_role (tenant_id, role_code);

CREATE INDEX sys_role_idx_status_sort ON sys_role (tenant_id, status, sort_no);

CREATE TABLE IF NOT EXISTS sys_user_role (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, scope_org_id BIGINT NOT NULL DEFAULT 0, valid_from DATETIME(3) NULL, valid_until DATETIME(3) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_user_role_uk_user_role_scope ON sys_user_role (tenant_id, user_id, role_id, scope_org_id);

CREATE INDEX sys_user_role_idx_role_user ON sys_user_role (tenant_id, role_id, user_id);

CREATE TABLE IF NOT EXISTS sys_menu (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, parent_id BIGINT NOT NULL DEFAULT 0, menu_code VARCHAR(64) NOT NULL, menu_name VARCHAR(100) NOT NULL, menu_type VARCHAR(20) NOT NULL, route_path VARCHAR(255) NULL, component_path VARCHAR(255) NULL, permission_code VARCHAR(128) NULL, icon VARCHAR(64) NULL, visible TINYINT NOT NULL DEFAULT 1, keep_alive TINYINT NOT NULL DEFAULT 0, external_url VARCHAR(500) NULL, sort_no INT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', deleted TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_menu_uk_tenant_menu_code ON sys_menu (tenant_id, menu_code);

CREATE INDEX sys_menu_idx_parent_sort ON sys_menu (tenant_id, parent_id, sort_no);

CREATE INDEX sys_menu_idx_permission ON sys_menu (tenant_id, permission_code);

CREATE TABLE IF NOT EXISTS sys_api_resource (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, resource_name VARCHAR(128) NOT NULL, permission_code VARCHAR(128) NOT NULL, http_method VARCHAR(16) NOT NULL, path_pattern VARCHAR(255) NOT NULL, controller_method VARCHAR(255) NULL, auth_mode VARCHAR(20) NOT NULL DEFAULT 'REQUIRED', status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', risk_level VARCHAR(16) NOT NULL DEFAULT 'NORMAL', created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_api_resource_uk_method_path ON sys_api_resource (tenant_id, http_method, path_pattern);

CREATE INDEX sys_api_resource_idx_permission ON sys_api_resource (tenant_id, permission_code);

CREATE TABLE IF NOT EXISTS sys_role_menu (tenant_id BIGINT NOT NULL DEFAULT 0, role_id BIGINT NOT NULL, menu_id BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (tenant_id, role_id, menu_id));

CREATE INDEX sys_role_menu_idx_menu_role ON sys_role_menu (tenant_id, menu_id, role_id);

CREATE TABLE IF NOT EXISTS sys_role_api (tenant_id BIGINT NOT NULL DEFAULT 0, role_id BIGINT NOT NULL, api_id BIGINT NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (tenant_id, role_id, api_id));

CREATE INDEX sys_role_api_idx_api_role ON sys_role_api (tenant_id, api_id, role_id);

CREATE TABLE IF NOT EXISTS sys_role_data_scope (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, role_id BIGINT NOT NULL, resource_code VARCHAR(128) NOT NULL, scope_type VARCHAR(20) NOT NULL, combine_mode VARCHAR(16) NOT NULL DEFAULT 'UNION', created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_role_data_scope_uk_role_resource ON sys_role_data_scope (tenant_id, role_id, resource_code);

CREATE TABLE IF NOT EXISTS sys_role_data_scope_org (tenant_id BIGINT NOT NULL DEFAULT 0, rule_id BIGINT NOT NULL, org_id BIGINT NOT NULL, PRIMARY KEY (tenant_id, rule_id, org_id));

CREATE INDEX sys_role_data_scope_org_idx_org_rule ON sys_role_data_scope_org (tenant_id, org_id, rule_id);

