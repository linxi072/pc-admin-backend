-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V6__user_single_role_org.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

DROP TABLE IF EXISTS sys_user_role;

DROP TABLE IF EXISTS sys_user_org;

ALTER TABLE sys_user ADD COLUMN role_id BIGINT NULL;

ALTER TABLE sys_user ADD COLUMN org_id BIGINT NULL;

CREATE INDEX idx_tenant_role ON sys_user (tenant_id, role_id);

CREATE INDEX idx_tenant_org ON sys_user (tenant_id, org_id);

