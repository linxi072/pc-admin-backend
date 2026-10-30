-- codegen-only 反向工程源（生产 Flyway 见 V40__announcement_read_receipt.sql）
-- 标识符一律显式加引号保留 snake_case 小写：H2 DDLDatabase 反向工程不会转大写，
-- 生成的 Tables.SYS_ANNOUNCEMENT_READ 列名小写，生产 MySQL 大小写敏感场景安全。
-- 索引名与既有 codegen 表去重（H2 索引名 schema 全局唯一）；已确认
-- uk_ann_user / idx_user / idx_ann 均未被占用（见全局 grep）。
-- 索引以独立 CREATE INDEX 语句声明（与 V17-V24 一致），避免 H2 将内联 KEY 误解析为 domain。
CREATE TABLE IF NOT EXISTS "sys_announcement_read" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "announcement_id" BIGINT NOT NULL,
    "user_id" BIGINT NOT NULL,
    "read_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "uk_ann_user" ON "sys_announcement_read" ("tenant_id", "announcement_id", "user_id");
CREATE INDEX "idx_user" ON "sys_announcement_read" ("tenant_id", "user_id");
CREATE INDEX "idx_ann" ON "sys_announcement_read" ("tenant_id", "announcement_id");
