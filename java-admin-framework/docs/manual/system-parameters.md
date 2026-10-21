# 系统参数表设计 & 硬编码配置值提取映射清单

> 配套迁移脚本：`src/main/resources/db/migration/V38__system_parameters.sql`
> 关联代码：`SysConfigDO` / `ConfigService` / `ConfigController`（`/api/system/configs`）
> 目标库：MySQL 8.x（InnoDB + utf8mb4 + utf8mb4_0900_ai_ci）

---

## 1. 背景与目标

系统中大量业务阈值、接口地址、开关标志、固定常量以**硬编码**形式散落在
`@ConfigurationProperties` 类、`*Rules` 纯规则类与 `@RateLimit` 注解中。一旦需要调整
（例如改短信网关地址、放宽单次导出行数、缩短令牌有效期），必须改代码、重新发版。

本方案将既有 `sys_config`（参数表）扩展为完整的「系统参数中心」，把硬编码值沉淀为可配置参数：

- 复用既有 `sys_config` 表与 `ConfigController`/`ConfigService`，**不新建重复表**；
- 补齐此前缺失的 **`default_value`（默认值）** 与 **`editable`（是否可编辑）** 两列，使表结构完整覆盖
  「参数编码 / 参数名称 / 参数值 / 默认值 / 描述 / 是否可编辑」；
- 提供**可直接在目标库执行的**初始化 SQL（建表语句 + 原硬编码值 INSERT）；
- 通过 `/api/system/configs` 或直连数据库即可运行时调参，无需发版。

---

## 2. 系统参数表结构设计（`sys_config`）

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | BIGINT UNSIGNED PK | 雪花 ID |
| `tenant_id` | BIGINT UNSIGNED DEFAULT 0 | 租户隔离（当前单租户，恒为 0） |
| `config_key` | VARCHAR(64) NOT NULL | **参数编码**（唯一键 `uk_tenant_config_key`） |
| `config_name` | VARCHAR(100) NOT NULL | **参数名称**（中文展示） |
| `config_value` | VARCHAR(500) NULL | **参数值**（当前生效值） |
| `default_value` | VARCHAR(500) NULL | **默认值**（工厂默认，区别于当前生效值） |
| `config_type` | VARCHAR(20) DEFAULT 'STRING' | 值类型：STRING / INT / BOOLEAN |
| `remark` | VARCHAR(255) NULL | **描述** |
| `editable` | TINYINT UNSIGNED NOT NULL DEFAULT 1 | **是否可编辑**：1=运行时可改，0=系统内置不可改 |
| `status` | VARCHAR(20) DEFAULT 'ACTIVE' | 状态 |
| `version` / `deleted` | 审计/逻辑删除 | 框架标准列 |
| `created_by` / `created_at` / `updated_by` / `updated_at` | 审计时间 | 框架标准列 |

完整建表语句见迁移脚本 `V38__system_parameters.sql` 顶部的
`CREATE TABLE IF NOT EXISTS sys_config (...)` 段落。

**语义约定**
- `config_value` = 当前生效值；`default_value` = 出厂默认值。种子数据两者相等；管理员改 `config_value`
  后，可用 `default_value` 一键回退。
- `editable = 0` 的参数属**身份 / 算法 / 密钥源**等结构性配置（如 JWT issuer、密钥源类型），不建议运行时随意修改。

---

## 3. 迁移脚本说明（V38）

脚本三段式、全程幂等，**可重复执行**：

1. `CREATE TABLE IF NOT EXISTS sys_config (...)`：含扩展列的完整建表语句，已存在则跳过（独立部署可用）。
2. 两个 `information_schema` 守卫的 `ALTER TABLE ... ADD COLUMN`：仅当列不存在时才加 `default_value` / `editable`。
3. `INSERT IGNORE INTO sys_config (...)`：将原硬编码值写入参数表（id 从 4 起，不与 V32 既有 1/2/3 行冲突）。

执行方式（常规 Flyway 流程）：应用启动时自动应用；或手动在目标库执行该文件。

---

## 4. 硬编码值 → 系统参数映射清单（已沉淀为种子）

> 值取自各 `Properties` 类默认值 / 规则类常量。类型列：`S`=STRING，`I`=INT，`B`=BOOLEAN；
> 时长以可读串表示（如 `30m`/`30d`/`15m`/`24h`/`60s`）。

### 4.1 安全 / JWT（`JwtProperties`，前缀 `app.security`）

| 参数编码 | 参数名称 | 值 | 类型 | 可编辑 | 原硬编码来源 |
| --- | --- | --- | --- | --- | --- |
| `security.jwt.issuer` | JWT 签发者(iss) | `java-admin-framework` | S | 0 | `JwtProperties.issuer` |
| `security.jwt.audience` | JWT 受众(aud) | `admin-api` | S | 0 | `JwtProperties.audience` |
| `security.jwt.access-token-ttl` | 访问令牌有效期 | `30m` | S | 1 | `accessTokenTtl` |
| `security.jwt.refresh-token-ttl` | 刷新令牌有效期 | `30d` | S | 1 | `refreshTokenTtl` |
| `security.jwt.max-login-failures` | 最大登录失败次数 | `5` | I | 1 | `maxLoginFailures` |
| `security.jwt.lock-duration` | 账户锁定时长 | `15m` | S | 1 | `lockDuration` |
| `security.jwt.lock-duration-max` | 锁定封顶时长 | `24h` | S | 1 | `lockDurationMax` |
| `security.jwt.password-history-limit` | 密码历史保留条数 | `5` | I | 1 | `passwordHistoryLimit` |
| `security.jwt.max-concurrent-sessions` | 最大并发会话数 | `3` | I | 1 | `maxConcurrentSessions` |
| `security.jwt.session-eviction-strategy` | 会话挤占策略 | `evict_oldest` | S | 1 | `sessionEvictionStrategy` |
| `security.jwt.session-max-inactive` | 会话最大空闲时长 | `30m` | S | 1 | `sessionMaxInactive` |
| `security.jwt.session-cleanup-enabled` | 会话空闲回收开关 | `true` | B | 1 | `sessionCleanupEnabled` |
| `security.jwt.session-cleanup-interval-ms` | 会话回收间隔(ms) | `300000` | I | 1 | `sessionCleanupIntervalMs` |
| `security.jwt.online-session-idle-threshold` | 在线会话空闲阈值 | `15m` | S | 1 | `onlineSessionIdleThreshold` |
| `security.jwt.online-session-heartbeat-interval` | 会话心跳间隔 | `60s` | S | 1 | `onlineSessionHeartbeatInterval` |
| `security.jwt.online-session-restriction-scan-interval-ms` | 登录限制扫描间隔(ms) | `60000` | I | 1 | `onlineSessionRestrictionScanIntervalMs` |

### 4.2 缓存（`CacheProperties`，前缀 `app.cache`）

| 参数编码 | 参数名称 | 值 | 类型 | 可编辑 | 原硬编码来源 |
| --- | --- | --- | --- | --- | --- |
| `cache.ttl` | 缓存默认 TTL | `30m` | S | 1 | `CacheProperties.ttl` |
| `cache.key-prefix` | 缓存 Key 前缀 | `acme:cache:` | S | 1 | `CacheProperties.keyPrefix` |

### 4.3 导入 / 导出

| 参数编码 | 参数名称 | 值 | 类型 | 可编辑 | 原硬编码来源 |
| --- | --- | --- | --- | --- | --- |
| `export.max-rows` | 单次导出最大行数 | `100000` | I | 1 | `ExportProperties.maxRows` |
| `import.max-rows` | 单次导入最大行数 | `5000` | I | 1 | `ImportProperties.maxRows` |
| `import.max-errors` | 导入回传最大错误数 | `200` | I | 1 | `ImportProperties.maxErrors` |

### 4.4 通知渠道

| 参数编码 | 参数名称 | 值 | 类型 | 可编辑 | 原硬编码来源 |
| --- | --- | --- | --- | --- | --- |
| `notification.sms.enabled` | 短信渠道开关 | `false` | B | 1 | `SmsProperties.enabled` |
| `notification.sms.provider` | 短信网关实现 | `LOG` | S | 1 | `SmsProperties.provider` |
| `notification.sms.timeout-ms` | 短信读写超时(ms) | `5000` | I | 1 | `SmsProperties.timeoutMs` |
| `notification.sms.connect-timeout-ms` | 短信连接超时(ms) | `2000` | I | 1 | `SmsProperties.connectTimeoutMs` |
| `notification.email.enabled` | 邮件渠道开关 | `false` | B | 1 | `EmailProperties.enabled` |
| `notification.email.host` | SMTP 主机 | `localhost` | S | 1 | `EmailProperties.host` |
| `notification.email.port` | SMTP 端口 | `25` | I | 1 | `EmailProperties.port` |
| `notification.email.starttls` | SMTP 启用 STARTTLS | `false` | B | 1 | `EmailProperties.starttls` |
| `notification.email.timeout-ms` | 邮件读写超时(ms) | `5000` | I | 1 | `EmailProperties.timeoutMs` |
| `notification.webhook.enabled` | Webhook 渠道开关 | `false` | B | 1 | `WebhookProperties.enabled` |
| `notification.webhook.timeout-ms` | Webhook 超时(ms) | `5000` | I | 1 | `WebhookProperties.timeoutMs` |
| `notification.webhook.connect-timeout-ms` | Webhook 连接超时(ms) | `2000` | I | 1 | `WebhookProperties.connectTimeoutMs` |

> 凭据类（短信 `gateway-url`/`app-id`/`app-key`/`signature`、邮件 `from`/`username`/`password`、Webhook `secret`）
> 经环境变量注入，**不入库**。

### 4.5 验证码 / 防重放 / 客户端 IP

| 参数编码 | 参数名称 | 值 | 类型 | 可编辑 | 原硬编码来源 |
| --- | --- | --- | --- | --- | --- |
| `captcha.expire-seconds` | 验证码有效期(s) | `120` | I | 1 | `CaptchaProperties.expireSeconds` |
| `captcha.max-number` | 加减法操作数上界 | `20` | I | 1 | `CaptchaProperties.maxNumber` |
| `captcha.mul-max-number` | 乘除法操作数上界 | `10` | I | 1 | `CaptchaProperties.mulMaxNumber` |
| `antireplay.max-clock-skew-ms` | 防重放时钟偏差(ms) | `5000` | I | 1 | `AntiReplayProperties.maxClockSkewMillis` |
| `antireplay.nonce-ttl-seconds` | 防重放 nonce 存活(s) | `60` | I | 1 | `AntiReplayProperties.nonceTtlSeconds` |
| `antireplay.max-nonce-cache-size` | 防重放 nonce 缓存上限 | `100000` | I | 1 | `AntiReplayProperties.maxNonceCacheSize` |
| `client-ip.trusted-proxy-count` | 可信代理跳数 | `1` | I | 1 | `ClientIpProperties.trustedProxyCount` |

### 4.6 可观测性 / 密钥源

| 参数编码 | 参数名称 | 值 | 类型 | 可编辑 | 原硬编码来源 |
| --- | --- | --- | --- | --- | --- |
| `observability.otlp.enabled` | OTLP 导出开关 | `false` | B | 1 | `ObservabilityProperties.Otlp.enabled` |
| `observability.otlp.endpoint` | OTLP traces 端点 | `http://localhost:4318/v1/traces` | S | 1 | `ObservabilityProperties.Otlp.endpoint` |
| `observability.otlp.timeout-ms` | OTLP 导出超时(ms) | `2000` | I | 1 | `ObservabilityProperties.Otlp.timeoutMillis` |
| `secret.provider` | 密钥源类型 | `local` | S | 0 | `SecretProperties.provider` |
| `secret.vault.address` | Vault 地址 | `http://localhost:8200` | S | 0 | `SecretProperties.Vault.address` |

### 4.7 限流默认（`@RateLimit` 注解值）

| 参数编码 | 参数名称 | 值 | 类型 | 可编辑 | 原硬编码来源 |
| --- | --- | --- | --- | --- | --- |
| `ratelimit.window-seconds` | 限流时间窗(s) | `60` | I | 1 | `@RateLimit.windowSeconds` |
| `ratelimit.config-update.max` | 配置更新限流上限 | `10` | I | 1 | `ConfigController.update` |
| `ratelimit.file.max` | 文件接口限流上限 | `20` | I | 1 | `FileController` |
| `ratelimit.import.max` | 导入接口限流上限 | `10` | I | 1 | `ImportController` |

---

## 5. 未纳入参数表的硬编码值（说明）

以下扫描到的硬编码值**未**写入 `sys_config`，原因：属于**算法 / 身份 / 框架固定契约**，
运行时改之无业务意义甚至破坏正确性，仍保留在代码中：

- **算法常数**：`IdGenerator.EPOCH=1700000000000`、`WORKER_ID_BITS=10`、`SEQUENCE_BITS=12`；
  `OrderingRules.MURMUR_SEED=0x9747b28c` 等；`TraceContext` 各 hex 长度。
- **身份/密钥标识**：`JwtProperties.keyId="1"`、`previousKeyId="0"`（密钥轮换标识，由密钥管理流程控制）。
- **框架内部阈值**：`PageQuery.MAX_SIZE=100`、`MAX_PAGE=100000`、`JooqSorts.MAX_SORT_FIELDS=3`、
  `MessageService.MAX_BATCH=5000`、`JobValidator.*`、`MultiDataSourceProperties` 连接池参数、
  `NPlusOneProbe.DEFAULT_THRESHOLD=10`、各 `*Probe`/`*Throttle` 内部上限等。
- **角色/口令策略码**：`AssigneeResolver.MANAGER_ROLE_CODE="MANAGER"`、弱口令黑名单（属安全策略常量）。

若后续希望把这些也纳入统一参数中心，可扩展 `config_type` 并补充对应 `getByKey` 读取逻辑。

---

## 6. 使用方式

- **界面 / API**：通过 `GET/POST/PUT/DELETE /api/system/configs` 管理（权限码 `system:config:*`）。
  返回与写入现已包含 `defaultValue` 与 `editable` 字段。
- **数据库直改**：`UPDATE sys_config SET config_value=? WHERE config_key=?;`（注意 `ConfigService` 内存缓存
  随写操作失效；直改库需重启或后续接入 Redis 才能立即生效）。
- **回退默认值**：将 `config_value` 重置为 `default_value` 即可。

---

## 7. 后续建议

1. 让各 `Properties` 类在缺失 yml 配置时**回退读取 `sys_config`**（如 `getByKey(...).getConfigValue()`），
   实现「代码默认值 → 参数表 → yml」三级覆盖，真正做到改参免发版。
2. `config_type` 扩展 `DURATION`/`JSON` 等类型，配合类型化解析。
3. 为 `editable=0` 的参数在界面置灰，避免误操作。
4. 接入 Redis 后让 `ConfigService` 缓存随库变更事件失效。
