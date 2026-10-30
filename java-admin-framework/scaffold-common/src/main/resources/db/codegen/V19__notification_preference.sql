-- codegen-only 反向工程源（生产 Flyway 见 V43__notification_preference.sql）
-- 标识符一律显式加引号保留 snake_case 小写：H2 DDLDatabase 反向工程不会转大写，
-- 生成的 Tables.SYS_NOTIFICATION_PREFERENCE 列名小写，生产 MySQL 大小写敏感场景安全。
-- 索引名与既有 codegen 表去重（H2 索引名 schema 全局唯一）：
--   uk_user_channel_type 已被 V17(sys_notification_digest) 占用 → 此处改名 uk_preference_user_channel_type
--   idx_tenant_user 已被 V18(sys_notification_quiet_hours) 占用 → 此处改名 idx_preference_tenant_user
CREATE TABLE IF NOT EXISTS "sys_notification_preference" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "user_id" BIGINT NOT NULL,
    "channel" VARCHAR(16) NOT NULL,
    "msg_type" VARCHAR(32) NOT NULL DEFAULT 'NOTICE',
    "enabled" TINYINT NOT NULL DEFAULT 1,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_preference_user_channel_type" ON "sys_notification_preference" ("user_id", "channel", "msg_type");

CREATE INDEX "idx_preference_tenant_user" ON "sys_notification_preference" ("tenant_id", "user_id");
