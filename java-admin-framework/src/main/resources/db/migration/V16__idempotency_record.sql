-- 幂等性记录表（设计方案 §12：写接口支持 Idempotency-Key，审批类接口强制使用）
--
-- 设计要点：
-- 1) 唯一索引 uk_idem_key_scope(tenant_id, idempotency_key, api_scope) 是并发去重的最終兜底：
--    即使应用层判断与插入之间有时间窗，数据库也会拒绝第二条登记，Aspect 捕获后返回 409。
-- 2) 记录与业务数据分属不同事务（Aspect 以 REQUIRES_NEW 写入），
--    因此业务回滚不会把幂等记录一并抹掉，避免「失败后又允许重复提交」。
-- 3) response_snapshot 用于重复请求时重放首次响应，做到「多次调用 = 一次效果 + 一致返回值」。
-- 4) expires_at 由 JobRunr 周期任务清理，避免表无限膨胀。

CREATE TABLE IF NOT EXISTS sys_idempotency_record (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    idempotency_key VARCHAR(128) NOT NULL,
    api_scope VARCHAR(128) NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    request_fingerprint VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PROCESSING',
    response_snapshot JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idem_key_scope (tenant_id, idempotency_key, api_scope),
    KEY idx_idem_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
