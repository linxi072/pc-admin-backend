-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V21__job_management.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.
-- 引号保留小写：与 Flyway V21 严格一致，避免 H2 反向工程把标识符转大写（B41）。

CREATE TABLE IF NOT EXISTS "sys_job" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "job_name" VARCHAR(128) NOT NULL,
    "job_group" VARCHAR(64) NOT NULL DEFAULT 'DEFAULT',
    "task_key" VARCHAR(128) NOT NULL,
    "trigger_type" VARCHAR(16) NOT NULL,
    "cron_expression" VARCHAR(128) NULL,
    "interval_value" INT NULL,
    "interval_unit" VARCHAR(16) NULL,
    "one_time_at" DATETIME(3) NULL,
    "job_params" VARCHAR(2048) NULL,
    "status" VARCHAR(16) NOT NULL DEFAULT 'PAUSED',
    "scheduler_ref" VARCHAR(64) NULL,
    "last_fire_time" DATETIME(3) NULL,
    "next_fire_time" DATETIME(3) NULL,
    "total_runs" BIGINT NOT NULL DEFAULT 0,
    "success_runs" BIGINT NOT NULL DEFAULT 0,
    "failure_runs" BIGINT NOT NULL DEFAULT 0,
    "last_status" VARCHAR(16) NULL,
    "last_error" VARCHAR(1024) NULL,
    "last_duration_ms" BIGINT NULL,
    "remark" VARCHAR(255) NULL,
    "version" INT NOT NULL DEFAULT 0,
    "deleted" TINYINT NOT NULL DEFAULT 0,
    "created_by" BIGINT NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    "updated_by" BIGINT NULL,
    "updated_at" DATETIME(3) NULL,
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_sys_job_status" ON "sys_job" ("tenant_id", "status", "deleted");

CREATE INDEX "idx_sys_job_task_key" ON "sys_job" ("task_key");

CREATE TABLE IF NOT EXISTS "sys_job_log" (
    "id" BIGINT NOT NULL,
    "tenant_id" BIGINT NOT NULL DEFAULT 0,
    "job_id" BIGINT NOT NULL,
    "job_name" VARCHAR(128) NULL,
    "task_key" VARCHAR(128) NULL,
    "trigger_type" VARCHAR(16) NULL,
    "fire_time" DATETIME(3) NOT NULL,
    "finished_time" DATETIME(3) NULL,
    "duration_ms" BIGINT NULL,
    "status" VARCHAR(16) NOT NULL,
    "error_message" VARCHAR(1024) NULL,
    "trace_id" VARCHAR(64) NULL,
    "created_at" DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY ("id")
);

CREATE INDEX "idx_sys_job_log_job" ON "sys_job_log" ("tenant_id", "job_id", "fire_time");
