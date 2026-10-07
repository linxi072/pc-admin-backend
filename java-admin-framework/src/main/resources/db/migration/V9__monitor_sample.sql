-- V9 新增功能模块：系统监控采样。
-- 表名 / 列名与 com.acme.scaffold.jooq.JooqTables 中的 SYS_MONITOR_SAMPLE 注册严格一致。
--
-- 定时任务(JobRunr)周期采样 CPU/内存/磁盘/JVM 与在线用户数，写入本表，
-- 供「按时间范围查询」与趋势图使用，避免实时计算带来的采集开销。
CREATE TABLE IF NOT EXISTS sys_monitor_sample (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    -- CPU 使用率(%)
    cpu_usage DECIMAL(5,2) NULL,
    -- JVM 堆内存使用率(%)
    memory_usage DECIMAL(5,2) NULL,
    -- 物理内存使用率(%)
    system_memory_usage DECIMAL(5,2) NULL,
    -- 磁盘使用率(%)
    disk_usage DECIMAL(5,2) NULL,
    used_heap_bytes BIGINT UNSIGNED NULL,
    max_heap_bytes BIGINT UNSIGNED NULL,
    used_memory_bytes BIGINT UNSIGNED NULL,
    total_memory_bytes BIGINT UNSIGNED NULL,
    -- 在线用户数(活跃刷新令牌/未过期会话)
    online_users INT UNSIGNED NOT NULL DEFAULT 0,
    active_sessions INT UNSIGNED NOT NULL DEFAULT 0,
    thread_count INT UNSIGNED NOT NULL DEFAULT 0,
    -- 采集时刻
    sampled_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_tenant_time (tenant_id, sampled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
