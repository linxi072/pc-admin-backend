-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V5__system_modules.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS sys_dict_type (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, dict_code VARCHAR(64) NOT NULL, dict_name VARCHAR(100) NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', sort_no INT NOT NULL DEFAULT 0, remark VARCHAR(255) NULL, version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_dict_type_uk_tenant_dict_code ON sys_dict_type (tenant_id, dict_code);

CREATE INDEX sys_dict_type_idx_status_sort ON sys_dict_type (tenant_id, status, sort_no);

CREATE TABLE IF NOT EXISTS sys_dict_data (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, dict_type_code VARCHAR(64) NOT NULL, dict_label VARCHAR(100) NOT NULL, dict_value VARCHAR(100) NOT NULL, dict_sort INT NOT NULL DEFAULT 0, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', remark VARCHAR(255) NULL, version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE INDEX sys_dict_data_idx_type_value ON sys_dict_data (tenant_id, dict_type_code, dict_value);

CREATE INDEX sys_dict_data_idx_type_sort ON sys_dict_data (tenant_id, dict_type_code, dict_sort);

CREATE TABLE IF NOT EXISTS sys_config (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, config_key VARCHAR(64) NOT NULL, config_name VARCHAR(100) NOT NULL, config_value VARCHAR(500) NULL, config_type VARCHAR(20) NOT NULL DEFAULT 'STRING', remark VARCHAR(255) NULL, status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_config_uk_tenant_config_key ON sys_config (tenant_id, config_key);

CREATE INDEX sys_config_idx_status ON sys_config (tenant_id, status);

