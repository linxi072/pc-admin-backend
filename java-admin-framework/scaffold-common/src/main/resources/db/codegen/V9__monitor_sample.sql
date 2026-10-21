-- AUTO-GENERATED for jOOQ DDLDatabase (H2-compatible). DO NOT EDIT BY HAND.
-- Derived from db/migration/V9__monitor_sample.sql by scripts/normalize-ddl-for-codegen.py
-- Regenerate after changing migrations.

CREATE TABLE IF NOT EXISTS sys_monitor_sample (id BIGINT NOT NULL, tenant_id BIGINT NOT NULL DEFAULT 0, cpu_usage DECIMAL(5,2) NULL, memory_usage DECIMAL(5,2) NULL, system_memory_usage DECIMAL(5,2) NULL, disk_usage DECIMAL(5,2) NULL, used_heap_bytes BIGINT NULL, max_heap_bytes BIGINT NULL, used_memory_bytes BIGINT NULL, total_memory_bytes BIGINT NULL, online_users INT NOT NULL DEFAULT 0, active_sessions INT NOT NULL DEFAULT 0, thread_count INT NOT NULL DEFAULT 0, sampled_at DATETIME(3) NOT NULL, PRIMARY KEY (id));

CREATE INDEX sys_monitor_sample_idx_tenant_time ON sys_monitor_sample (tenant_id, sampled_at);

