-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V45__notification_digest.sql.
-- 标识符统一加双引号以保留小写（H2 反向工程默认把未加引号的标识符转大写，
-- 而生产 MySQL 表名区分大小写，故生成层必须保持 snake_case 小写以与 Flyway 迁移严格一致）。
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS "sys_notification_digest" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "user_id" BIGINT NOT NULL,
    "channel" VARCHAR(16) NOT NULL,
    "msg_type" VARCHAR(32) NOT NULL,
    "item_count" INT NOT NULL DEFAULT 0,
    "items_json" TEXT NULL,
    "first_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "last_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_user_channel_type" ON "sys_notification_digest" ("user_id", "channel", "msg_type");
