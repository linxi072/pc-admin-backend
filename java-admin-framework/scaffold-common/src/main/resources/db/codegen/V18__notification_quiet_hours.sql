-- codegen-only 反向工程源（生产 Flyway 见 V44__notification_quiet_hours.sql）
-- 标识符一律显式加引号保留 snake_case 小写：H2 DDLDatabase 反向工程不会转大写，
-- 生成的 Tables.SYS_NOTIFICATION_QUIET_HOURS 列名小写，生产 MySQL 大小写敏感场景安全。
CREATE TABLE IF NOT EXISTS "sys_notification_quiet_hours" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "user_id" BIGINT NOT NULL,
    "enabled" TINYINT NOT NULL DEFAULT 1,
    "start_time" TIME NOT NULL,
    "end_time" TIME NOT NULL,
    "alert_breakthrough" TINYINT NOT NULL DEFAULT 1,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_user" ON "sys_notification_quiet_hours" ("user_id");

CREATE INDEX "idx_tenant_user" ON "sys_notification_quiet_hours" ("tenant_id", "user_id");
