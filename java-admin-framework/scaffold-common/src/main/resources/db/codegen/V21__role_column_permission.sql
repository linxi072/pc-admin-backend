-- codegen-only 反向工程源（生产 Flyway 见 V61__role_column_permission.sql）
-- 标识符一律显式加引号保留 snake_case 小写：H2 DDLDatabase 反向工程不会转大写，
-- 生成的 Tables.SYS_ROLE_COLUMN_PERMISSION 列名小写，生产 MySQL 大小写敏感场景安全。
-- 索引名与既有 codegen 表去重（H2 索引名 schema 全局唯一）；此处用 rcp 前缀避免冲突。
CREATE TABLE IF NOT EXISTS "sys_role_column_permission" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "role_id" BIGINT NOT NULL,
    "resource_code" VARCHAR(128) NOT NULL,
    "field" VARCHAR(128) NOT NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_rcp_role_resource_field" ON "sys_role_column_permission" ("tenant_id", "role_id", "resource_code", "field");

CREATE INDEX "idx_rcp_role" ON "sys_role_column_permission" ("tenant_id", "role_id");

CREATE INDEX "idx_rcp_resource" ON "sys_role_column_permission" ("tenant_id", "resource_code");
