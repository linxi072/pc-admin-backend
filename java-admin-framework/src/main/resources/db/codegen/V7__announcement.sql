-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V7__announcement.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS sys_announcement (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, title VARCHAR(200) NOT NULL, content TEXT NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', is_top TINYINT NOT NULL DEFAULT 0, publish_at DATETIME(3) NULL, expire_at DATETIME(3) NULL, published_at DATETIME(3) NULL, offline_at DATETIME(3) NULL, publisher_id BIGINT NULL, view_count INT NOT NULL DEFAULT 0, version INT NOT NULL DEFAULT 0, deleted TINYINT NOT NULL DEFAULT 0, created_by BIGINT NULL, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), updated_by BIGINT NULL, updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE INDEX sys_announcement_idx_tenant_status_pub ON sys_announcement (tenant_id, status, publish_at);

CREATE INDEX sys_announcement_idx_tenant_top ON sys_announcement (tenant_id, is_top, publish_at);

CREATE INDEX sys_announcement_idx_expire ON sys_announcement (tenant_id, status, expire_at);

