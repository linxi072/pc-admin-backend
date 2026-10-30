-- codegen-only 反向工程源（生产 Flyway 见 V42__notification_record.sql）
-- 标识符一律显式加引号保留 snake_case 小写：H2 DDLDatabase 反向工程不会转大写，
-- 生成的 Tables.SYS_NOTIFICATION_RECORD 列名小写，生产 MySQL 大小写敏感场景安全。
-- 索引名与既有 codegen 表去重（H2 索引名 schema 全局唯一）：
--   idx_tenant_time / idx_status_time 已被 V23(sys_sms_record) 占用，
--   故本表改用 idx_notif_tenant_time / idx_notif_status_time；idx_channel_time 未被占用，沿用。
-- 索引以独立 CREATE INDEX 语句声明（与 V17-V23 一致），避免 H2 将内联 KEY 误解析为 domain。
-- sys_notification_record 无 deleted 列：记录即审计，永久留存，JooqWriters 自动跳过逻辑删除。
CREATE TABLE IF NOT EXISTS "sys_notification_record" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "channel" VARCHAR(16) NOT NULL,
    "template_code" VARCHAR(64) NULL,
    "msg_type" VARCHAR(32) NULL,
    "title_summary" VARCHAR(256) NOT NULL,
    "content_summary" VARCHAR(512) NOT NULL,
    "receiver_summary" VARCHAR(512) NOT NULL,
    "receiver_count" INT NOT NULL DEFAULT 0,
    "delivered" INT NOT NULL DEFAULT 0,
    "status" VARCHAR(16) NOT NULL,
    "error_message" VARCHAR(512) NULL,
    "operator_id" BIGINT NULL,
    "operator_name" VARCHAR(64) NULL,
    "trace_id" VARCHAR(64) NULL,
    "sent_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_notif_tenant_time" ON "sys_notification_record" ("tenant_id", "sent_at");
CREATE INDEX "idx_channel_time" ON "sys_notification_record" ("channel", "sent_at");
CREATE INDEX "idx_notif_status_time" ON "sys_notification_record" ("status", "sent_at");
