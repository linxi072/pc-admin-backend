-- M43 OAuth2 令牌治理：签发令牌注册表（记录 jti + 元数据，支持查看/吊销，吊销后强制失效）。
-- 与 M38–M40 自包含 RS256 令牌签发互补：此前令牌为无状态 JWT，无法在签发后撤销；
-- 本表把每次签发的 jti 落库，吊销（status=REVOKED）经 OAuth2TokenRevocationFilter 在请求期实时拒绝。
-- 设计要点：
--   1) jti 唯一（uk_jti），与 OAuth2TokenProvider 签发时写入 JWT 的 id 声明严格一致；
--   2) status 仅 ACTIVE / REVOKED；列表查询以 status=ACTIVE 且 expires_at>now 定义「活动」；
--   3) 软删除（deleted）+ 乐观锁（version）与既有表一致；tenant_id 恒为 0（平台维表，由平台统一维护）。

CREATE TABLE IF NOT EXISTS sys_oauth2_token (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    jti VARCHAR(64) NOT NULL,
    client_id VARCHAR(128) NOT NULL,
    user_id BIGINT NULL,
    username VARCHAR(128) NULL,
    scope VARCHAR(512) NULL,
    token_type VARCHAR(20) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_jti (tenant_id, jti),
    KEY idx_client (tenant_id, client_id),
    KEY idx_user (tenant_id, user_id),
    KEY idx_status_exp (tenant_id, status, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
