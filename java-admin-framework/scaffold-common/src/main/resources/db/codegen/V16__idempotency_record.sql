-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V16__idempotency_record.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS sys_idempotency_record (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, idempotency_key VARCHAR(128) NOT NULL, api_scope VARCHAR(128) NOT NULL, user_id BIGINT NULL, request_fingerprint VARCHAR(64) NULL, status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING', response_snapshot VARCHAR(4000) NULL, error_code VARCHAR(64) NULL, error_message VARCHAR(500) NULL, expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_at DATETIME(3) NULL, PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_idempotency_record_uk_idem_key_scope ON sys_idempotency_record (tenant_id, idempotency_key, api_scope);

CREATE INDEX sys_idempotency_record_idx_idem_expires ON sys_idempotency_record (expires_at);

