-- ============================================================
-- 模块参考 SQL：系统参数
-- 说明：参数配置表
-- 来源：db/migration/ 中对应版本迁移（原文拼接，未经改写）
-- 依赖：需先存在 V32 基线；本文件为逻辑抽取，仅供阅读/追溯，
--       不可作为独立迁移执行（Flyway 只扫描 db/migration/）。
-- 重新生成：python3 scripts/build_reference_modular.py
-- ============================================================

-- ============================================================
-- Source migration: V38__system_parameters.sql
-- ============================================================

-- =====================================================================================
-- V38 系统参数表扩展 + 硬编码配置值沉淀
-- -------------------------------------------------------------------------------------
-- 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）
-- 目的：
--   1) 为已存在的系统参数表 sys_config 增加两个缺失字段：
--        default_value  —— 参数默认值（工厂默认值，区别于当前生效值 config_value）
--        editable       —— 是否可编辑（1=运行时可改 / 0=系统内置不可改）
--      使表结构完整覆盖「参数编码 / 参数名称 / 参数值 / 默认值 / 描述 / 是否可编辑」。
--   2) 将原散落在 Java @ConfigurationProperties / 规则类中的硬编码配置值
--      （业务阈值、接口地址、开关标志、固定常量）沉淀为可配置参数种子，
--      以便运维在 /api/system/configs 界面或数据库直接调参，无需改代码发版。
--
-- 幂等说明（重要）：
--   - 建表用 CREATE TABLE IF NOT EXISTS（已存在则跳过，便于独立部署场景直接执行）；
--   - 加列用 information_schema 守卫做幂等 ALTER（重复执行不报 1060）；
--   - 种子用 INSERT IGNORE（重复执行不报 1062，且不与 V32 既有 1/2/3 行冲突）。
--   可直接在目标库重复执行。
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- 1) 完整建表语句（参考 / 独立部署用）：与 V32 列定义严格一致，并补齐 default_value / editable
--    常规 Flyway 流程下该语句为 no-op（sys_config 已由 V32 创建）。
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_config (
    id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    config_key VARCHAR(64) NOT NULL,
    config_name VARCHAR(100) NOT NULL,
    config_value VARCHAR(500) NULL,
    config_type VARCHAR(20) NOT NULL DEFAULT 'STRING',
    remark VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version INT UNSIGNED NOT NULL DEFAULT 0,
    deleted TINYINT UNSIGNED NOT NULL DEFAULT 0,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_by BIGINT UNSIGNED NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    default_value VARCHAR(500) NULL COMMENT '参数默认值（工厂默认值，区别于当前生效值 config_value）',
    editable TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '是否可编辑：1=运行时可改，0=系统内置不可改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_config_key (tenant_id, config_key),
    KEY idx_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- -------------------------------------------------------------------------------------
-- 2) 幂等扩展：为既有 sys_config 补列（仅当列不存在时才加）
-- -------------------------------------------------------------------------------------
SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_config` ADD COLUMN `default_value` VARCHAR(500) NULL COMMENT ''参数默认值（工厂默认值，区别于当前生效值 config_value）''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config' AND COLUMN_NAME = 'default_value'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE `sys_config` ADD COLUMN `editable` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT ''是否可编辑：1=运行时可改，0=系统内置不可改''',
        'DO 0')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_config' AND COLUMN_NAME = 'editable'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- -------------------------------------------------------------------------------------
-- 3) 种子数据：将原硬编码配置值写入参数表
--    列序：id, tenant_id, config_key(参数编码), config_name(参数名称),
--          config_value(参数值), default_value(默认值), config_type, remark(描述),
--          editable(是否可编辑), status, created_at, updated_at
--    editable=0 的参数属身份/算法/密钥源等结构性配置，不建议运行时随意修改。
-- -------------------------------------------------------------------------------------
INSERT IGNORE INTO sys_config (id, tenant_id, config_key, config_name, config_value, default_value, config_type, remark, editable, status, created_at, updated_at)
VALUES
    -- ===== 安全 / JWT =====
    (4, 0, 'security.jwt.issuer', 'JWT 签发者(iss)', 'java-admin-framework', 'java-admin-framework', 'STRING', 'JWT issuer 声明，身份标识，通常不改', 0, 'ACTIVE', NOW(3), NOW(3)),
    (5, 0, 'security.jwt.audience', 'JWT 受众(aud)', 'admin-api', 'admin-api', 'STRING', 'JWT audience 声明，身份标识，通常不改', 0, 'ACTIVE', NOW(3), NOW(3)),
    (6, 0, 'security.jwt.access-token-ttl', '访问令牌有效期', '30m', '30m', 'STRING', 'access_token 过期时长，建议 15~60m', 1, 'ACTIVE', NOW(3), NOW(3)),
    (7, 0, 'security.jwt.refresh-token-ttl', '刷新令牌有效期', '30d', '30d', 'STRING', 'refresh_token 过期时长', 1, 'ACTIVE', NOW(3), NOW(3)),
    (8, 0, 'security.jwt.max-login-failures', '最大登录失败次数', '5', '5', 'INT', '超过则锁定账户', 1, 'ACTIVE', NOW(3), NOW(3)),
    (9, 0, 'security.jwt.lock-duration', '账户锁定时长', '15m', '15m', 'STRING', '渐进式锁定起算时长', 1, 'ACTIVE', NOW(3), NOW(3)),
    (10, 0, 'security.jwt.lock-duration-max', '锁定封顶时长', '24h', '24h', 'STRING', '渐进式锁定时长上限', 1, 'ACTIVE', NOW(3), NOW(3)),
    (11, 0, 'security.jwt.password-history-limit', '密码历史保留条数', '5', '5', 'INT', '禁止复用最近 N 次密码', 1, 'ACTIVE', NOW(3), NOW(3)),
    (12, 0, 'security.jwt.max-concurrent-sessions', '最大并发会话数', '3', '3', 'INT', '同账号最大在线设备/会话，0=不限制', 1, 'ACTIVE', NOW(3), NOW(3)),
    (13, 0, 'security.jwt.session-eviction-strategy', '会话挤占策略', 'evict_oldest', 'evict_oldest', 'STRING', 'reject=拒绝新登录; evict_oldest=淘汰最早会话', 1, 'ACTIVE', NOW(3), NOW(3)),
    (14, 0, 'security.jwt.session-max-inactive', '会话最大空闲时长', '30m', '30m', 'STRING', '刷新令牌空闲超此值被回收', 1, 'ACTIVE', NOW(3), NOW(3)),
    (15, 0, 'security.jwt.session-cleanup-enabled', '会话空闲回收开关', 'true', 'true', 'BOOLEAN', '是否启用空闲回收定时任务', 1, 'ACTIVE', NOW(3), NOW(3)),
    (16, 0, 'security.jwt.session-cleanup-interval-ms', '会话回收间隔(ms)', '300000', '300000', 'INT', '空闲回收定时任务执行间隔', 1, 'ACTIVE', NOW(3), NOW(3)),
    (17, 0, 'security.jwt.online-session-idle-threshold', '在线会话空闲阈值', '15m', '15m', 'STRING', '超过标记为「空闲」(仅展示)', 1, 'ACTIVE', NOW(3), NOW(3)),
    (18, 0, 'security.jwt.online-session-heartbeat-interval', '会话心跳间隔', '60s', '60s', 'STRING', '心跳写库节流间隔', 1, 'ACTIVE', NOW(3), NOW(3)),
    (19, 0, 'security.jwt.online-session-restriction-scan-interval-ms', '登录限制扫描间隔(ms)', '60000', '60000', 'INT', '锁定到期自动解除扫描间隔', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 缓存 =====
    (20, 0, 'cache.ttl', '缓存默认 TTL', '30m', '30m', 'STRING', '@Cacheable 未显式指定时生效', 1, 'ACTIVE', NOW(3), NOW(3)),
    (21, 0, 'cache.key-prefix', '缓存 Key 前缀', 'acme:cache:', 'acme:cache:', 'STRING', 'Redis key 前缀，便于运维排查与批量清理', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 导入 / 导出 =====
    (22, 0, 'export.max-rows', '单次导出最大行数', '100000', '100000', 'INT', '不含表头，超出则拒绝', 1, 'ACTIVE', NOW(3), NOW(3)),
    (23, 0, 'import.max-rows', '单次导入最大行数', '5000', '5000', 'INT', '不含表头，超出则拒绝', 1, 'ACTIVE', NOW(3), NOW(3)),
    (24, 0, 'import.max-errors', '导入回传最大错误数', '200', '200', 'INT', '超出的失败行仅计入统计不回传明细', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 通知渠道 =====
    (25, 0, 'notification.sms.enabled', '短信渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发与费用', 1, 'ACTIVE', NOW(3), NOW(3)),
    (26, 0, 'notification.sms.provider', '短信网关实现', 'LOG', 'LOG', 'STRING', 'LOG=仅记录脱敏日志; HTTP=通用网关', 1, 'ACTIVE', NOW(3), NOW(3)),
    (27, 0, 'notification.sms.timeout-ms', '短信读写超时(ms)', '5000', '5000', 'INT', 'HTTP 模式读写超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (28, 0, 'notification.sms.connect-timeout-ms', '短信连接超时(ms)', '2000', '2000', 'INT', 'HTTP 模式连接超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (29, 0, 'notification.email.enabled', '邮件渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发', 1, 'ACTIVE', NOW(3), NOW(3)),
    (30, 0, 'notification.email.host', 'SMTP 主机', 'localhost', 'localhost', 'STRING', 'SMTP 服务器地址', 1, 'ACTIVE', NOW(3), NOW(3)),
    (31, 0, 'notification.email.port', 'SMTP 端口', '25', '25', 'INT', '25/465/587 依服务商', 1, 'ACTIVE', NOW(3), NOW(3)),
    (32, 0, 'notification.email.starttls', 'SMTP 启用 STARTTLS', 'false', 'false', 'BOOLEAN', '587 通常为 true', 1, 'ACTIVE', NOW(3), NOW(3)),
    (33, 0, 'notification.email.timeout-ms', '邮件读写超时(ms)', '5000', '5000', 'INT', '读写超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (34, 0, 'notification.webhook.enabled', 'Webhook 渠道开关', 'false', 'false', 'BOOLEAN', '缺省关闭避免误发', 1, 'ACTIVE', NOW(3), NOW(3)),
    (35, 0, 'notification.webhook.timeout-ms', 'Webhook 超时(ms)', '5000', '5000', 'INT', '读写超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (36, 0, 'notification.webhook.connect-timeout-ms', 'Webhook 连接超时(ms)', '2000', '2000', 'INT', '连接超时', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 验证码 / 防重放 / 客户端 IP =====
    (37, 0, 'captcha.expire-seconds', '验证码有效期(s)', '120', '120', 'INT', '验证码超时时长', 1, 'ACTIVE', NOW(3), NOW(3)),
    (38, 0, 'captcha.max-number', '加减法操作数上界', '20', '20', 'INT', '加法/减法操作数 a,b ∈ [1,maxNumber]', 1, 'ACTIVE', NOW(3), NOW(3)),
    (39, 0, 'captcha.mul-max-number', '乘除法操作数上界', '10', '10', 'INT', '乘法因子/除法除数与商上界', 1, 'ACTIVE', NOW(3), NOW(3)),
    (40, 0, 'antireplay.max-clock-skew-ms', '防重放时钟偏差(ms)', '5000', '5000', 'INT', '允许的服务端/客户端时钟偏差上限', 1, 'ACTIVE', NOW(3), NOW(3)),
    (41, 0, 'antireplay.nonce-ttl-seconds', '防重放 nonce 存活(s)', '60', '60', 'INT', '超时后允许同 nonce 再次使用', 1, 'ACTIVE', NOW(3), NOW(3)),
    (42, 0, 'antireplay.max-nonce-cache-size', '防重放 nonce 缓存上限', '100000', '100000', 'INT', '超出后按过期时间惰性清理', 1, 'ACTIVE', NOW(3), NOW(3)),
    (43, 0, 'client-ip.trusted-proxy-count', '可信代理跳数', '1', '1', 'INT', '解析 X-Forwarded-For 跳数，0=直连公网', 1, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 可观测性 / 密钥源 =====
    (44, 0, 'observability.otlp.enabled', 'OTLP 导出开关', 'false', 'false', 'BOOLEAN', '需配置 Collector 后开启', 1, 'ACTIVE', NOW(3), NOW(3)),
    (45, 0, 'observability.otlp.endpoint', 'OTLP traces 端点', 'http://localhost:4318/v1/traces', 'http://localhost:4318/v1/traces', 'STRING', 'Collector 接收地址', 1, 'ACTIVE', NOW(3), NOW(3)),
    (46, 0, 'observability.otlp.timeout-ms', 'OTLP 导出超时(ms)', '2000', '2000', 'INT', '导出 HTTP 超时', 1, 'ACTIVE', NOW(3), NOW(3)),
    (47, 0, 'secret.provider', '密钥源类型', 'local', 'local', 'STRING', 'local=环境变量/随机临时; vault=HashiCorp Vault', 0, 'ACTIVE', NOW(3), NOW(3)),
    (48, 0, 'secret.vault.address', 'Vault 地址', 'http://localhost:8200', 'http://localhost:8200', 'STRING', 'Vault 服务地址', 0, 'ACTIVE', NOW(3), NOW(3)),

    -- ===== 限流默认 =====
    (49, 0, 'ratelimit.window-seconds', '限流时间窗(s)', '60', '60', 'INT', '默认限流窗口', 1, 'ACTIVE', NOW(3), NOW(3)),
    (50, 0, 'ratelimit.config-update.max', '配置更新限流上限', '10', '10', 'INT', '配置更新接口每分钟上限', 1, 'ACTIVE', NOW(3), NOW(3)),
    (51, 0, 'ratelimit.file.max', '文件接口限流上限', '20', '20', 'INT', '文件接口每分钟上限', 1, 'ACTIVE', NOW(3), NOW(3)),
    (52, 0, 'ratelimit.import.max', '导入接口限流上限', '10', '10', 'INT', '导入接口每分钟上限', 1, 'ACTIVE', NOW(3), NOW(3));

