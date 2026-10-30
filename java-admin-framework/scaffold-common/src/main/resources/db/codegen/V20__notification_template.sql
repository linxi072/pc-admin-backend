-- codegen-only 反向工程源（生产 Flyway 见 V41__notification_template.sql）
-- 标识符一律显式加引号保留 snake_case 小写：H2 DDLDatabase 反向工程不会转大写，
-- 生成的 Tables.SYS_NOTIFICATION_TEMPLATE 列名小写，生产 MySQL 大小写敏感场景安全。
-- 索引名与既有 codegen 表去重（H2 索引名 schema 全局唯一）；此处沿用生产 V41 命名，
-- 已确认 uk_tenant_code / idx_tenant_channel / idx_tenant_enabled 均未被占用。
CREATE TABLE IF NOT EXISTS "sys_notification_template" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "template_code" VARCHAR(64) NOT NULL,
    "template_name" VARCHAR(100) NOT NULL,
    "channel" VARCHAR(16) NOT NULL,
    "title" VARCHAR(200) NULL,
    "content" TEXT NOT NULL,
    "msg_type" VARCHAR(16) NOT NULL DEFAULT 'NOTICE',
    "enabled" TINYINT NOT NULL DEFAULT 1,
    "remark" VARCHAR(255) NULL,
    "version" INT NOT NULL DEFAULT 0,
    "deleted" INT NOT NULL DEFAULT 0,
    "created_by" BIGINT NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_by" BIGINT NULL,
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_tenant_code" ON "sys_notification_template" ("tenant_id", "template_code");

CREATE INDEX "idx_tenant_channel" ON "sys_notification_template" ("tenant_id", "channel");

CREATE INDEX "idx_tenant_enabled" ON "sys_notification_template" ("tenant_id", "enabled");
