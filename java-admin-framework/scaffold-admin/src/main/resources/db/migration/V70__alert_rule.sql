-- V70 告警规则闭环（V1.0.5 #922）：新增 alert_rule 表，支撑可配置阈值规则的持久化与周期评估。
--
-- 设计要点：
--   1) alert_rule 独立新表，与既有 wf_*/sys_* 表 BIGINT UNSIGNED / utf8mb4 约定一致。
--   2) 指标键(metric_key)/比较符(comparator)/严重度(severity)/渠道类型(channel_type) 均以
--      字符串存储枚举名（由 AlertRuleRules 解析校验），不动表结构即可横向扩展枚举。
--   3) 逻辑删除(deleted) + 乐观锁(version) + 审计时间(created_at/updated_at) 与仓库其他表一致；
--      created_by/updated_by 允许 NULL（规则可由调度线程创建/更新，无操作人上下文）。
--   4) 零新接口权限手工登记：管理端点 @PreAuthorize 由 ApiResourceAutoSyncRunner 扫描自动落库并授予 SUPER_ADMIN。
--   5) 本迁移向前追加，幂等依赖 Flyway 版本控制；重复执行会因表已存在报错，需按 Flyway 标准流程 baseline/repair。

CREATE TABLE IF NOT EXISTS alert_rule (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    rule_name VARCHAR(128) NOT NULL,
    metric_key VARCHAR(32) NOT NULL,
    comparator VARCHAR(8) NOT NULL,
    threshold DECIMAL(12, 4) NOT NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'WARNING',
    for_duration_sec INT NOT NULL DEFAULT 0,
    consecutive_count INT NOT NULL DEFAULT 1,
    channel_type VARCHAR(16) NOT NULL DEFAULT 'INBOX',
    channel_config VARCHAR(1024) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 0,
    remark VARCHAR(255) NULL,
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_metric_enabled (metric_key, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
