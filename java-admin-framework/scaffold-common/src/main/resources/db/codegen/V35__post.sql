-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V35__post_management.sql.
-- 标识符统一加双引号以保留小写（H2 反向工程默认把未加引号的标识符转大写，
-- 而生产 MySQL 表名区分大小写，故生成层必须保持 snake_case 小写以与 Flyway 迁移严格一致）。
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS "sys_post" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "post_code" VARCHAR(64) NOT NULL,
    "post_name" VARCHAR(100) NOT NULL,
    "post_sort" INT NOT NULL DEFAULT 0,
    "status" VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    "remark" VARCHAR(512) NULL,
    "version" INT NOT NULL DEFAULT 0,
    "deleted" TINYINT NOT NULL DEFAULT 0,
    "created_by" BIGINT NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_by" BIGINT NULL,
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_tenant_post_code" ON "sys_post" ("tenant_id", "post_code");

CREATE INDEX "idx_status_sort" ON "sys_post" ("tenant_id", "status", "post_sort");

CREATE TABLE IF NOT EXISTS "sys_user_post" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "user_id" BIGINT NOT NULL,
    "post_id" BIGINT NOT NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_user_post" ON "sys_user_post" ("tenant_id", "user_id", "post_id");

CREATE INDEX "idx_post_user" ON "sys_user_post" ("tenant_id", "post_id", "user_id");
