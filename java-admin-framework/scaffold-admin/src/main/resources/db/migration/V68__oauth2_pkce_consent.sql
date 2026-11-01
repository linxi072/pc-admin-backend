-- V68 OAuth2 授权码流程强化（V1.0.5 #900）：redirect_uri 白名单 + PKCE 强制开关 + 用户同意(consent)持久化。
--
-- 设计要点：
--   1) sys_openapi_client 增两列：
--        - redirect_uris   ：授权码回调地址白名单（逗号分隔，空白=不约束，兼容存量客户端）。
--        - pkce_required   ：是否强制 PKCE（授权码流程），默认 0（不强制），新注册_public 客户端建议置 1。
--      与 V48 同表，沿用其 BIGINT UNSIGNED / utf8mb4 约定（H2 测试与 MySQL 生产一致）。
--   2) 新增 sys_oauth2_consent：资源拥有者对客户端的授权同意记录。
--        - 一行 = 一次「用户 X 对客户端 C 在 scopes 集合上 APPROVED/DENIED」；
--        - 「记住授权」写 APPROVED 行；授权端点查 (user_id, client_id, scopes⊆已授权) 的未过期 APPROVED 行，
--          命中则直接签发授权码，否则要求用户再次确认（返回 consent_required）。
--        - 无 deleted/version：同意记录采用「追加新行」语义，历史可追溯，无需逻辑删除与乐观锁。
--   3) 依赖：V48（sys_openapi_client 已存在）。本迁移幂等（ALTER 幂等依赖 Flyway 版本控制，重复执行会报已存在列，
--      故仅向前追加重跑；如需本地重置，按 Flyway 标准流程 baseline/repair）。

-- 1) sys_openapi_client 增强列
ALTER TABLE sys_openapi_client
    ADD COLUMN redirect_uris VARCHAR(1024) NULL COMMENT '授权码回调地址白名单（逗号分隔，空白=不约束）';

ALTER TABLE sys_openapi_client
    ADD COLUMN pkce_required TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否强制 PKCE（授权码流程）';

-- 2) 用户同意记录表
CREATE TABLE IF NOT EXISTS sys_oauth2_consent (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    user_id BIGINT NOT NULL,
    client_id VARCHAR(64) NOT NULL,
    scopes VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'APPROVED',
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_user_client (user_id, client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
