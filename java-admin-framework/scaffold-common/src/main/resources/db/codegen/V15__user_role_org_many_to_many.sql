-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V15__user_role_org_many_to_many.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE sys_user_role (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, user_id BIGINT NOT NULL, role_id BIGINT NOT NULL, is_primary TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_user_role_uk_user_role ON sys_user_role (tenant_id, user_id, role_id);

CREATE INDEX sys_user_role_idx_role ON sys_user_role (tenant_id, role_id);

CREATE INDEX sys_user_role_idx_user ON sys_user_role (tenant_id, user_id);

CREATE TABLE sys_user_org (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, user_id BIGINT NOT NULL, org_id BIGINT NOT NULL, is_primary TINYINT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), PRIMARY KEY (id));

CREATE UNIQUE INDEX sys_user_org_uk_user_org ON sys_user_org (tenant_id, user_id, org_id);

CREATE INDEX sys_user_org_idx_org ON sys_user_org (tenant_id, org_id);

CREATE INDEX sys_user_org_idx_user ON sys_user_org (tenant_id, user_id);

-- 归一化 DDL 仅供 jOOQ DDLDatabase 反向工程：先删依赖索引再删列，得到最终 schema。
-- jOOQ DDL 解析器不支持 DROP INDEX/DROP COLUMN 的 IF EXISTS 形式，故此处用无 IF EXISTS 语句。

DROP INDEX idx_tenant_role;

DROP INDEX idx_tenant_org;

DROP INDEX sys_user_idx_org_status;

ALTER TABLE sys_user DROP COLUMN role_id;

ALTER TABLE sys_user DROP COLUMN org_id;

ALTER TABLE sys_user DROP COLUMN primary_org_id;

