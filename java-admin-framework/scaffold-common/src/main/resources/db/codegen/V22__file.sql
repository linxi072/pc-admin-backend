-- codegen-only 反向工程源（生产 Flyway 见 V33__file_management.sql + V50__file_dedup.sql）
-- 标识符一律显式加引号保留 snake_case 小写：H2 DDLDatabase 反向工程不会转大写，
-- 生成的 Tables.SYS_FILE 列名小写，生产 MySQL 大小写敏感场景安全。
-- 索引名与既有 codegen 表去重（H2 索引名 schema 全局唯一）；此处沿用生产 V33 命名，
-- 已确认 idx_tenant_source / idx_tenant_uploader / idx_tenant_created / idx_content_hash 均未被占用。
-- 索引以独立 CREATE INDEX 语句声明（与 V17-V21 一致），避免 H2 将内联 KEY 误解析为 domain。
CREATE TABLE IF NOT EXISTS "sys_file" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "original_name" VARCHAR(255) NOT NULL,
    "stored_name" VARCHAR(255) NOT NULL,
    "storage_path" VARCHAR(500) NOT NULL,
    "file_size" BIGINT NOT NULL,
    "content_type" VARCHAR(128) NULL,
    "content_hash" CHAR(64) NULL,
    "source_module" VARCHAR(64) NOT NULL,
    "biz_id" VARCHAR(64) NULL,
    "uploader_id" BIGINT NULL,
    "uploader_name" VARCHAR(100) NULL,
    "download_count" BIGINT NOT NULL DEFAULT 0,
    "version" INT NOT NULL DEFAULT 0,
    "deleted" TINYINT NOT NULL DEFAULT 0,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_tenant_source" ON "sys_file" ("tenant_id", "source_module");
CREATE INDEX "idx_tenant_uploader" ON "sys_file" ("tenant_id", "uploader_id");
CREATE INDEX "idx_tenant_created" ON "sys_file" ("tenant_id", "created_at");
CREATE INDEX "idx_content_hash" ON "sys_file" ("content_hash");
