-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V8__message.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS sys_message (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, title VARCHAR(200) NOT NULL, content TEXT NOT NULL, msg_type VARCHAR(20) NOT NULL DEFAULT 'NOTICE', sender_id BIGINT NULL, filter_role_ids VARCHAR(500) NULL, filter_org_ids VARCHAR(500) NULL, receiver_ids VARCHAR(500) NULL, total_count INT NOT NULL DEFAULT 0, read_count INT NOT NULL DEFAULT 0, sent_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE INDEX sys_message_idx_tenant_sent ON sys_message (tenant_id, sent_at);

CREATE TABLE IF NOT EXISTS sys_message_receipt (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, message_id BIGINT NOT NULL, user_id BIGINT NOT NULL, is_read TINYINT NOT NULL DEFAULT 0, read_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_message_receipt_uk_msg_user ON sys_message_receipt (tenant_id, message_id, user_id);

CREATE INDEX sys_message_receipt_idx_user_read ON sys_message_receipt (tenant_id, user_id, is_read);

CREATE INDEX sys_message_receipt_idx_msg ON sys_message_receipt (tenant_id, message_id);

