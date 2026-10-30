-- codegen-only 反向工程源（生产 Flyway 见 V37__sms_record.sql）
-- 标识符一律显式加引号保留 snake_case 小写：H2 DDLDatabase 反向工程不会转大写，
-- 生成的 Tables.SYS_SMS_RECORD 列名小写，生产 MySQL 大小写敏感场景安全。
-- 索引名与既有 codegen 表去重（H2 索引名 schema 全局唯一）；此处沿用生产 V37 命名，
-- 已确认 idx_tenant_time / idx_status_time 均未被占用（V9 的 sys_monitor_sample_idx_tenant_time 名称不同）。
-- 索引以独立 CREATE INDEX 语句声明（与 V17-V22 一致），避免 H2 将内联 KEY 误解析为 domain。
-- sys_sms_record 无 deleted 列：记录即审计，永久留存，JooqWriters 自动跳过逻辑删除。
CREATE TABLE IF NOT EXISTS "sys_sms_record" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "batch_no" VARCHAR(64) NOT NULL,
    "mobiles_masked" VARCHAR(1024) NOT NULL,
    "mobile_count" INT NOT NULL DEFAULT 0,
    "content_summary" VARCHAR(256) NOT NULL,
    "provider" VARCHAR(16) NOT NULL,
    "status" VARCHAR(16) NOT NULL,
    "error_message" VARCHAR(512) NULL,
    "operator_id" BIGINT NULL,
    "operator_name" VARCHAR(64) NULL,
    "trace_id" VARCHAR(64) NULL,
    "sent_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_tenant_time" ON "sys_sms_record" ("tenant_id", "sent_at");
CREATE INDEX "idx_status_time" ON "sys_sms_record" ("status", "sent_at");
