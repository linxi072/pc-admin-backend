-- V48 API 开放平台客户端注册与密钥治理（M24）：sys_openapi_client，把「外部系统如何接入本平台」
-- 从散落的口头约定收口为可治理的客户端注册表（client_id 唯一 + 密钥哈希存储 + 轮换/停用/过期）。
--
-- 设计要点：
--   1) 立项前离线核验结论（2026-10-25）：离线仓库仅有 spring-security-oauth2-core/jose/
--      resource-server 6.3.3，**不含** spring-security-oauth2-client（RegisteredClient 所在）与
--      spring-security-oauth2-authorization-server（授权服务器所在）——完整 OAuth2 授权服务器
--      （authorization code / token 端点）离线不可建。本期收口为**自包含的客户端注册 + 密钥治理**
--      （自有领域模型 + DelegatingPasswordEncoder 哈希），不引任何新依赖；协议级开放（/oauth2/token
--      等）待联网环境补齐依赖后另行立项。
--   2) 密钥安全：client_secret 仅以 DelegatingPasswordEncoder 哈希落库（与用户口令同机制），
--      明文仅在创建/轮换成功时返回一次（ClientSecretView），任何查询端点不回放明文。
--   3) 密钥治理：rotateSecret 重新生成（SecureRandom 32 字节 → Base64 URL 无填充 43 字符）并
--      刷新 secret_rotated_at；toggle 停用/启用；expired_at 可选过期时间。
--   4) 结构校验集中在 system.openapi.rules.OpenApiClientRules（纯函数）：client_id 格式、
--      scope 解析与格式、状态白名单、过期时间须晚于当前。
--   5) 接口资源（system:openapi-client:read / :manage）不在此手工登记，
--      由 ApiResourceAutoSyncRunner 启动时扫描 @PreAuthorize 自动落库并授予 SUPER_ADMIN。
--   6) 依赖：V32（含「系统管理」一级目录 id=1002，menu_code='system'）。

-- 1) 表：sys_openapi_client（逻辑删除 + 乐观锁；(tenant_id, client_id) 唯一约束兜底并发注册）
CREATE TABLE IF NOT EXISTS sys_openapi_client (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    client_id VARCHAR(64) NOT NULL,
    client_name VARCHAR(128) NOT NULL,
    client_secret_hash VARCHAR(128) NOT NULL,
    scopes VARCHAR(512) NOT NULL DEFAULT 'read',
    status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
    secret_rotated_at DATETIME(3) NULL,
    expired_at DATETIME(3) NULL,
    remark VARCHAR(255) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_client (tenant_id, client_id),
    KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 2) 菜单：开放平台客户端（挂在「系统管理」1002 下，sort_no=112 紧随低代码表单试点之后）
INSERT IGNORE INTO sys_menu (id, tenant_id, parent_id, menu_code, menu_name, menu_type, route_path, component_path, permission_code, icon, visible, keep_alive, sort_no, status, created_at, updated_at)
VALUES (1122, 0, 1002, 'system:openapi-client', '开放平台客户端', 'MENU', '/system/openapi-clients', 'system/OpenApiClient', 'system:openapi-client:read', 'Key', 1, 0, 112, 'ACTIVE', NOW(3), NOW(3));

-- 3) 绑定 SUPER_ADMIN（role_id=1），保证超管可见可用
INSERT IGNORE INTO sys_role_menu (tenant_id, role_id, menu_id)
SELECT 0, 1, 1122;
