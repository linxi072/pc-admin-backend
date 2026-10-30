-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V5__system_modules.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.
-- 标识符统一加双引号以保留小写（H2 反向工程默认把未加引号的标识符转大写，
-- 而生产 MySQL 表名区分大小写，故生成层必须保持 snake_case 小写以与 Flyway 迁移严格一致）。
-- 此处 sys_dict_type / sys_dict_data / sys_config 三表一并加引号，保证生成的
-- Tables.SYS_DICT_TYPE / SYS_DICT_DATA / SYS_CONFIG 表名与列名均为小写，与运行时一致。

CREATE TABLE IF NOT EXISTS "sys_dict_type" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "dict_code" VARCHAR(64) NOT NULL,
    "dict_name" VARCHAR(100) NOT NULL,
    "status" VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    "sort_no" INT NOT NULL DEFAULT 0,
    "remark" VARCHAR(255) NULL,
    "version" INT NOT NULL DEFAULT 0,
    "deleted" TINYINT NOT NULL DEFAULT 0,
    "created_by" BIGINT NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_by" BIGINT NULL,
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_tenant_dict_code" ON "sys_dict_type" ("tenant_id", "dict_code");

CREATE INDEX "idx_dict_type_status_sort" ON "sys_dict_type" ("tenant_id", "status", "sort_no");

CREATE TABLE IF NOT EXISTS "sys_dict_data" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "dict_type_code" VARCHAR(64) NOT NULL,
    "dict_label" VARCHAR(100) NOT NULL,
    "dict_value" VARCHAR(100) NOT NULL,
    "dict_sort" INT NOT NULL DEFAULT 0,
    "status" VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    "remark" VARCHAR(255) NULL,
    "version" INT NOT NULL DEFAULT 0,
    "deleted" TINYINT NOT NULL DEFAULT 0,
    "created_by" BIGINT NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_by" BIGINT NULL,
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_type_value" ON "sys_dict_data" ("tenant_id", "dict_type_code", "dict_value");

CREATE INDEX "idx_type_sort" ON "sys_dict_data" ("tenant_id", "dict_type_code", "dict_sort");

CREATE TABLE IF NOT EXISTS "sys_config" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "config_key" VARCHAR(64) NOT NULL,
    "config_name" VARCHAR(100) NOT NULL,
    "config_value" VARCHAR(500) NULL,
    "default_value" VARCHAR(500) NULL,
    "config_type" VARCHAR(20) NOT NULL DEFAULT 'STRING',
    "remark" VARCHAR(255) NULL,
    "editable" TINYINT NOT NULL DEFAULT 1,
    "status" VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    "version" INT NOT NULL DEFAULT 0,
    "deleted" TINYINT NOT NULL DEFAULT 0,
    "created_by" BIGINT NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_by" BIGINT NULL,
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_tenant_config_key" ON "sys_config" ("tenant_id", "config_key");

CREATE INDEX "idx_status" ON "sys_config" ("tenant_id", "status");
